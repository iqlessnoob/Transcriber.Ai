import 'dart:io';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:file_picker/file_picker.dart';
import 'package:share_plus/share_plus.dart';
import 'package:ffmpeg_kit_flutter_new/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_new/return_code.dart';

import 'package:sherpa_onnx/sherpa_onnx.dart' as sherpa;

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  sherpa.initBindings();
  runApp(const TranscriberApp());
}

class TranscriberApp extends StatelessWidget {
  const TranscriberApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Transcriber.AI',
      theme: ThemeData.dark().copyWith(
        primaryColor: Colors.deepPurple,
        scaffoldBackgroundColor: const Color(0xFF121212),
      ),
      home: const ChatScreen(),
    );
  }
}

class ChatScreen extends StatefulWidget {
  const ChatScreen({Key? key}) : super(key: key);

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  // App State
  bool _isDownloadingEngine = false;
  double _downloadProgress = 0.0;
  bool _isProcessing = false;
  String _statusText = "";
  
  // Storage & Chats
  late SharedPreferences _prefs;
  List<Map<String, dynamic>> _chatHistory = [];
  String _currentChatId = "";
  String _currentSrtText = "";

  // Engine Paths
  late String _modelDir;

  @override
  void initState() {
    super.initState();
    _initApp();
  }

  Future<void> _initApp() async {
    _prefs = await SharedPreferences.getInstance();
    final docDir = await getApplicationDocumentsDirectory();
    _modelDir = '${docDir.path}/whisper_model';
    _loadChatHistory();

    bool hasDownloaded = _prefs.getBool('engine_downloaded_whisper') ?? false;
    if (!hasDownloaded) {
      await _downloadOfflineEngine();
    }
  }

  void _loadChatHistory() {
    String? historyJson = _prefs.getString('chat_history');
    if (historyJson != null) {
      List<dynamic> decoded = jsonDecode(historyJson);
      setState(() {
        _chatHistory = decoded.map((e) => Map<String, dynamic>.from(e)).toList();
      });
    }
  }

  Future<void> _saveChatHistory() async {
    await _prefs.setString('chat_history', jsonEncode(_chatHistory));
  }

  void _startNewChat() {
    setState(() {
      _currentChatId = DateTime.now().millisecondsSinceEpoch.toString();
      _currentSrtText = "";
      _statusText = "";
    });
  }

  Future<void> _downloadOfflineEngine() async {
    setState(() {
      _isDownloadingEngine = true;
      _statusText = "Downloading AI Engine (approx 40MB)...";
    });

    final dio = Dio();
    final urls = {
      'encoder.int8.onnx': 'https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-encoder.int8.onnx',
      'decoder.int8.onnx': 'https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-decoder.int8.onnx',
      'tokens.txt': 'https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en/resolve/main/tiny.en-tokens.txt',
    };

    Directory dir = Directory(_modelDir);
    if (!await dir.exists()) await dir.create(recursive: true);

    int totalFiles = urls.length;
    int completed = 0;

    for (var entry in urls.entries) {
      String savePath = '${dir.path}/${entry.key}';
      await dio.download(entry.value, savePath, onReceiveProgress: (rec, total) {
        setState(() {
          _downloadProgress = (completed / totalFiles) + ((rec / total) / totalFiles);
        });
      });
      completed++;
    }

    await _prefs.setBool('engine_downloaded_whisper', true);
    setState(() {
      _isDownloadingEngine = false;
      _statusText = "Engine Ready.";
    });
  }

  String _formatTime(double totalSeconds) {
    int hours = totalSeconds ~/ 3600;
    int minutes = ((totalSeconds % 3600) ~/ 60);
    int seconds = (totalSeconds % 60).toInt();
    int ms = ((totalSeconds - totalSeconds.truncate()) * 1000).toInt();
    return "${hours.toString().padLeft(2, '0')}:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')},${ms.toString().padLeft(3, '0')}";
  }
  Future<void> _processVideo() async {
    FilePickerResult? result = await FilePicker.platform.pickFiles(type: FileType.video);
    if (result == null || result.files.single.path == null) return;

    String videoPath = result.files.single.path!;
    
    setState(() {
      _isProcessing = true;
      _statusText = "Extracting Audio (This may take a minute)...";
      _currentChatId = DateTime.now().millisecondsSinceEpoch.toString();
      _currentSrtText = "";
    });

    final docDir = await getApplicationDocumentsDirectory();
    final outputDir = '${docDir.path}/audio_chunks_$_currentChatId';
    await Directory(outputDir).create();

    // 1. FFmpeg 7-Second Chunking Logic
    // We changed segment_time to 7 seconds so subtitles appear line-by-line!
    String ffmpegCmd = "-y -i '$videoPath' -vn -acodec pcm_s16le -ar 16000 -ac 1 -f segment -segment_time 7 '$outputDir/chunk_%04d.wav'";
    final session = await FFmpegKit.execute(ffmpegCmd);
    final returnCode = await session.getReturnCode();

    if (!ReturnCode.isSuccess(returnCode)) {
      setState(() {
        _isProcessing = false;
        _statusText = "Error extracting audio.";
      });
      return;
    }

    // 2. Initialize Sherpa-ONNX Engine with Whisper Tiny
    final config = sherpa.OfflineRecognizerConfig(
      model: sherpa.OfflineModelConfig(
        whisper: sherpa.OfflineWhisperModelConfig(
          encoder: '$_modelDir/encoder.int8.onnx',
          decoder: '$_modelDir/decoder.int8.onnx',
        ),
        tokens: '$_modelDir/tokens.txt',
        modelType: 'whisper',
      ),
    );
    final recognizer = sherpa.OfflineRecognizer(config);

    // 3. Sequential Processing & Time Offset
    List<FileSystemEntity> chunkFiles = Directory(outputDir).listSync().whereType<File>().toList();
    chunkFiles.sort((a, b) => a.path.compareTo(b.path));

    StringBuffer finalSrt = StringBuffer();
    int srtIndex = 1;

    for (int i = 0; i < chunkFiles.length; i++) {
      setState(() {
        _statusText = "Transcribing Part ${i + 1} of ${chunkFiles.length}...";
      });

      double timeOffset = i * 7.0; // 7 seconds offset per chunk
      
      final wave = sherpa.readWave(chunkFiles[i].path);
      final stream = recognizer.createStream();
      stream.acceptWaveform(sampleRate: wave.sampleRate, samples: wave.samples);
      
      recognizer.decode(stream);
      final streamResult = recognizer.getResult(stream);
      
      // .trim() cleans up any random spaces the AI spits out
      if (streamResult.text.trim().isNotEmpty) {
        String startTime = _formatTime(timeOffset);
        String endTime = _formatTime(timeOffset + (wave.samples.length / wave.sampleRate));
        
        finalSrt.writeln((srtIndex).toString());
        finalSrt.writeln("$startTime --> $endTime");
        finalSrt.writeln(streamResult.text.trim());
        finalSrt.writeln();
        srtIndex++;
      }
      stream.free();
    }
    
    recognizer.free();

    // 4. Save to Chat History
    String newSrt = finalSrt.toString();
    Map<String, dynamic> newChat = {
      'id': _currentChatId,
      'title': 'Video ${_chatHistory.length + 1}',
      'srt': newSrt,
    };
    
    setState(() {
      _chatHistory.insert(0, newChat);
      _currentSrtText = newSrt;
      _isProcessing = false;
      _statusText = "Transcription Complete!";
    });
    
    await _saveChatHistory();
    Directory(outputDir).deleteSync(recursive: true); // Cleanup
  }


  void _exportSubtitles() {
    if (_currentSrtText.isEmpty) return;
    Share.shareXFiles(
      [
        XFile.fromData(
          utf8.encode(_currentSrtText), 
          name: 'subtitles_$_currentChatId.srt', 
          mimeType: 'application/x-subrip'
        )
      ],
      text: 'Here are the generated subtitles!',
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Transcriber.AI"),
        actions: [
          IconButton(
            icon: const Icon(Icons.add_box),
            tooltip: "New Chat",
            onPressed: _isProcessing ? null : _startNewChat,
          )
        ],
      ),
      drawer: Drawer(
        child: Column(
          children: [
            const DrawerHeader(
              decoration: BoxDecoration(color: Colors.deepPurple),
              child: Center(child: Text("Chat History", style: TextStyle(color: Colors.white, fontSize: 20))),
            ),
            Expanded(
              child: ListView.builder(
                itemCount: _chatHistory.length,
                itemBuilder: (context, index) {
                  final chat = _chatHistory[index];
                  return ListTile(
                    leading: const Icon(Icons.chat_bubble_outline),
                    title: Text(chat['title']),
                    onTap: () {
                      setState(() {
                        _currentChatId = chat['id'];
                        _currentSrtText = chat['srt'];
                      });
                      Navigator.pop(context);
                    },
                    trailing: IconButton(
                      icon: const Icon(Icons.delete_outline, color: Colors.red),
                      onPressed: () {
                        setState(() {
                          _chatHistory.removeAt(index);
                          if (_currentChatId == chat['id']) {
                            _currentSrtText = "";
                          }
                        });
                        _saveChatHistory();
                      },
                    ),
                  );
                },
              ),
            )
          ],
        ),
      ),
      body: _isDownloadingEngine
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const CircularProgressIndicator(),
                  const SizedBox(height: 20),
                  Text(_statusText),
                  const SizedBox(height: 10),
                  Text("${(_downloadProgress * 100).toStringAsFixed(1)}%"),
                ],
              ),
            )
          : Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                children: [
                  Expanded(
                    child: Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12.0),
                      decoration: BoxDecoration(
                        color: Colors.white10,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: SingleChildScrollView(
                        child: SelectableText(
                          _currentSrtText.isEmpty ? "Upload a video to begin transcription..." : _currentSrtText,
                          style: const TextStyle(fontSize: 16),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  if (_isProcessing)
                    Column(
                      children: [
                        const CircularProgressIndicator(),
                        const SizedBox(height: 8),
                        Text(_statusText),
                      ],
                    ),
                  if (!_isProcessing)
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        ElevatedButton.icon(
                          onPressed: _processVideo,
                          icon: const Icon(Icons.video_file),
                          label: const Text("Select Video"),
                        ),
                        if (_currentSrtText.isNotEmpty)
                          ElevatedButton.icon(
                            onPressed: _exportSubtitles,
                            icon: const Icon(Icons.download),
                            label: const Text("Download .srt"),
                            style: ElevatedButton.styleFrom(backgroundColor: Colors.green),
                          ),
                      ],
                    )
                ],
              ),
            ),
    );
  }
}
