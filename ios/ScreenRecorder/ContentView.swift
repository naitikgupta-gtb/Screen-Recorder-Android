import SwiftUI
import AVKit

struct ContentView: View {
    @StateObject private var recorder = ScreenRecorderManager.shared
    @State private var selectedResolution = "1080p Full HD"
    @State private var selectedFps = "60 FPS"
    @State private var showingShareSheet = false
    @State private var videoToShare: URL?
    @State private var showingPlayer = false
    @State private var videoToPlay: URL?
    
    let resolutions = ["1080p Full HD", "720p HD", "480p SD"]
    let frameRates = ["60 FPS", "30 FPS", "24 FPS"]
    
    var body: some View {
        NavigationView {
            ZStack {
                Color(red: 0.05, green: 0.07, blue: 0.11)
                    .ignoresSafeArea()
                
                // Background Ambient Glow
                RadialGradient(
                    gradient: Gradient(colors: [Color.indigo.opacity(0.2), Color.clear]),
                    center: .top,
                    startRadius: 50,
                    endRadius: 400
                )
                .ignoresSafeArea()
                
                ScrollView {
                    VStack(spacing: 24) {
                        // Header Title
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text("Screen Recorder")
                                    .font(.system(size: 26, weight: .bold, design: .rounded))
                                    .foregroundColor(.white)
                                Text("iOS Edition • Ultra HD Capture")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                            Spacer()
                            
                            // Live Status Badge
                            HStack(spacing: 6) {
                                Circle()
                                    .fill(recorder.isRecording ? Color.red : Color.green)
                                    .frame(width: 8, height: 8)
                                Text(recorder.isRecording ? "RECORDING" : "READY")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(recorder.isRecording ? .red : .green)
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Color.white.opacity(0.06))
                            .clipShape(Capsule())
                        }
                        .padding(.horizontal)
                        .padding(.top, 10)
                        
                        // Main Recording Card
                        VStack(spacing: 20) {
                            // Timer Display
                            VStack(spacing: 6) {
                                Text(recorder.formattedDuration())
                                    .font(.system(size: 54, weight: .heavy, design: .monospaced))
                                    .foregroundColor(recorder.isRecording ? .red : .white)
                                
                                Text(recorder.statusMessage)
                                    .font(.footnote)
                                    .foregroundColor(.gray)
                            }
                            .padding(.top, 10)
                            
                            // Primary Record / Stop Button
                            Button(action: {
                                withAnimation(.spring()) {
                                    if recorder.isRecording {
                                        recorder.stopRecording()
                                    } else {
                                        recorder.startRecording()
                                    }
                                }
                            }) {
                                HStack(spacing: 12) {
                                    Image(systemName: recorder.isRecording ? "stop.fill" : "record.circle.fill")
                                        .font(.title2)
                                    Text(recorder.isRecording ? "Stop Recording" : "Start Recording")
                                        .font(.headline)
                                        .fontWeight(.bold)
                                }
                                .frame(maxWidth: .infinity)
                                .frame(height: 58)
                                .background(
                                    LinearGradient(
                                        colors: recorder.isRecording 
                                            ? [Color.red, Color.orange] 
                                            : [Color.indigo, Color.purple],
                                        startPoint: .leading,
                                        endPoint: .trailing
                                    )
                                )
                                .foregroundColor(.white)
                                .clipShape(RoundedRectangle(cornerRadius: 18))
                                .shadow(
                                    color: (recorder.isRecording ? Color.red : Color.indigo).opacity(0.4),
                                    radius: 12,
                                    y: 6
                                )
                            }
                            .padding(.horizontal)
                        }
                        .padding(.vertical, 24)
                        .background(Color.white.opacity(0.04))
                        .clipShape(RoundedRectangle(cornerRadius: 24))
                        .overlay(
                            RoundedRectangle(cornerRadius: 24)
                                .stroke(Color.white.opacity(0.08), lineWidth: 1)
                        )
                        .padding(.horizontal)
                        
                        // Quick Settings
                        VStack(alignment: .leading, spacing: 14) {
                            Text("Capture Settings")
                                .font(.headline)
                                .foregroundColor(.white)
                                .padding(.horizontal)
                            
                            VStack(spacing: 1) {
                                // Microphone Toggle
                                Toggle(isOn: Binding(
                                    get: { recorder.isMicEnabled },
                                    set: { _ in recorder.toggleMicrophone() }
                                )) {
                                    HStack(spacing: 12) {
                                        Image(systemName: "mic.fill")
                                            .foregroundColor(.indigo)
                                        VStack(alignment: .leading) {
                                            Text("Microphone Audio")
                                                .font(.subheadline)
                                                .foregroundColor(.white)
                                            Text("Record voice commentary")
                                                .font(.caption2)
                                                .foregroundColor(.gray)
                                        }
                                    }
                                }
                                .padding()
                                
                                Divider().background(Color.white.opacity(0.06))
                                
                                // Quality Selector
                                HStack {
                                    HStack(spacing: 12) {
                                        Image(systemName: "sparkles.tv")
                                            .foregroundColor(.purple)
                                        Text("Resolution")
                                            .font(.subheadline)
                                            .foregroundColor(.white)
                                    }
                                    Spacer()
                                    Picker("Resolution", selection: $selectedResolution) {
                                        ForEach(resolutions, id: \.self) { res in
                                            Text(res).tag(res)
                                        }
                                    }
                                    .pickerStyle(MenuPickerStyle())
                                }
                                .padding()
                                
                                Divider().background(Color.white.opacity(0.06))
                                
                                // Frame Rate Selector
                                HStack {
                                    HStack(spacing: 12) {
                                        Image(systemName: "speedometer")
                                            .foregroundColor(.blue)
                                        Text("Frame Rate")
                                            .font(.subheadline)
                                            .foregroundColor(.white)
                                    }
                                    Spacer()
                                    Picker("FPS", selection: $selectedFps) {
                                        ForEach(frameRates, id: \.self) { fps in
                                            Text(fps).tag(fps)
                                        }
                                    }
                                    .pickerStyle(MenuPickerStyle())
                                }
                                .padding()
                            }
                            .background(Color.white.opacity(0.04))
                            .clipShape(RoundedRectangle(cornerRadius: 18))
                            .overlay(
                                RoundedRectangle(cornerRadius: 18)
                                    .stroke(Color.white.opacity(0.08), lineWidth: 1)
                            )
                            .padding(.horizontal)
                        }
                        
                        // Recent Recordings Section
                        VStack(alignment: .leading, spacing: 14) {
                            HStack {
                                Text("Recent Recordings")
                                    .font(.headline)
                                    .foregroundColor(.white)
                                Spacer()
                                Text("\(recorder.recordedVideos.count) saved")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                            .padding(.horizontal)
                            
                            if recorder.recordedVideos.isEmpty {
                                VStack(spacing: 10) {
                                    Image(systemName: "video.slash")
                                        .font(.system(size: 32))
                                        .foregroundColor(.gray)
                                    Text("No recordings yet")
                                        .font(.subheadline)
                                        .foregroundColor(.gray)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 36)
                                .background(Color.white.opacity(0.02))
                                .clipShape(RoundedRectangle(cornerRadius: 16))
                                .padding(.horizontal)
                            } else {
                                ForEach(recorder.recordedVideos, id: \.self) { url in
                                    HStack(spacing: 14) {
                                        Image(systemName: "play.circle.fill")
                                            .font(.title2)
                                            .foregroundColor(.indigo)
                                        
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(url.lastPathComponent)
                                                .font(.subheadline)
                                                .foregroundColor(.white)
                                                .lineLimit(1)
                                            Text("Saved to Camera Roll")
                                                .font(.caption2)
                                                .foregroundColor(.green)
                                        }
                                        
                                        Spacer()
                                        
                                        // Play Button
                                        Button(action: {
                                            videoToPlay = url
                                            showingPlayer = true
                                        }) {
                                            Image(systemName: "play.fill")
                                                .font(.caption)
                                                .padding(8)
                                                .background(Color.white.opacity(0.08))
                                                .clipShape(Circle())
                                                .foregroundColor(.white)
                                        }
                                        
                                        // Share Button
                                        Button(action: {
                                            videoToShare = url
                                            showingShareSheet = true
                                        }) {
                                            Image(systemName: "square.and.arrow.up")
                                                .font(.caption)
                                                .padding(8)
                                                .background(Color.white.opacity(0.08))
                                                .clipShape(Circle())
                                                .foregroundColor(.white)
                                        }
                                    }
                                    .padding()
                                    .background(Color.white.opacity(0.04))
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                                    .padding(.horizontal)
                                }
                            }
                        }
                    }
                    .padding(.bottom, 40)
                }
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showingShareSheet) {
                if let url = videoToShare {
                    ShareSheet(activityItems: [url])
                }
            }
            .sheet(isPresented: $showingPlayer) {
                if let url = videoToPlay {
                    VideoPlayerView(url: url)
                }
            }
        }
    }
}

// Share Sheet Helper
struct ShareSheet: UIViewControllerRepresentable {
    var activityItems: [Any]
    
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: nil)
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

// In-app Video Player Helper
struct VideoPlayerView: View {
    let url: URL
    
    var body: some View {
        VideoPlayer(player: AVPlayer(url: url))
            .edgesIgnoringSafeArea(.all)
    }
}
