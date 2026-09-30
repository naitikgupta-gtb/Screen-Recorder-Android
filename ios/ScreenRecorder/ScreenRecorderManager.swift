import Foundation
import ReplayKit
import Photos
import Combine

class ScreenRecorderManager: NSObject, ObservableObject {
    static let shared = ScreenRecorderManager()
    
    private let recorder = RPScreenRecorder.shared()
    
    @Published var isRecording: Bool = false
    @Published var isPaused: Bool = false
    @Published var duration: TimeInterval = 0
    @Published var isMicEnabled: Bool = true
    @Published var lastSavedURL: URL?
    @Published var recordedVideos: [URL] = []
    @Published var statusMessage: String = "Ready to record"
    @Published var errorMessage: String?
    
    private var timer: Timer?
    
    override init() {
        super.init()
        loadRecordedVideos()
    }
    
    func startRecording() {
        guard recorder.isAvailable else {
            self.errorMessage = "Screen recording is not available on this device right now."
            return
        }
        
        recorder.isMicrophoneEnabled = isMicEnabled
        
        // Start ReplayKit in-app recording
        recorder.startRecording { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    self?.errorMessage = error.localizedDescription
                    self?.isRecording = false
                    return
                }
                
                self?.isRecording = true
                self?.duration = 0
                self?.statusMessage = "Recording in progress..."
                self?.startTimer()
            }
        }
    }
    
    func stopRecording() {
        let outputDirectory = FileManager.default.temporaryDirectory
        let outputURL = outputDirectory.appendingPathComponent("ScreenRecording_\(Int(Date().timeIntervalSince1970)).mp4")
        
        recorder.stopRecording(withOutput: outputURL) { [weak self] error in
            DispatchQueue.main.async {
                self?.stopTimer()
                self?.isRecording = false
                
                if let error = error {
                    self?.errorMessage = error.localizedDescription
                    return
                }
                
                self?.saveToPhotosLibrary(videoURL: outputURL)
            }
        }
    }
    
    private func saveToPhotosLibrary(videoURL: URL) {
        PHPhotoLibrary.requestAuthorization { [weak self] status in
            guard status == .authorized || status == .limited else {
                DispatchQueue.main.async {
                    self?.errorMessage = "Permission to save to Photos was denied."
                }
                return
            }
            
            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: videoURL)
            }) { success, error in
                DispatchQueue.main.async {
                    if success {
                        self?.statusMessage = "Saved to Photos & Camera Roll!"
                        self?.lastSavedURL = videoURL
                        self?.loadRecordedVideos()
                    } else if let error = error {
                        self?.errorMessage = error.localizedDescription
                    }
                }
            }
        }
    }
    
    private func startTimer() {
        stopTimer()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            DispatchQueue.main.async {
                self?.duration += 1
            }
        }
    }
    
    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }
    
    func toggleMicrophone() {
        isMicEnabled.toggle()
        recorder.isMicrophoneEnabled = isMicEnabled
    }
    
    func loadRecordedVideos() {
        let tempDir = FileManager.default.temporaryDirectory
        if let files = try? FileManager.default.contentsOfDirectory(at: tempDir, includingPropertiesForKeys: [.creationDateKey], options: .skipsHiddenFiles) {
            self.recordedVideos = files.filter { $0.pathExtension.lowercased() == "mp4" }
                .sorted { (url1, url2) -> Bool in
                    let date1 = (try? url1.resourceValues(forKeys: [.creationDateKey]))?.creationDate ?? Date.distantPast
                    let date2 = (try? url2.resourceValues(forKeys: [.creationDateKey]))?.creationDate ?? Date.distantPast
                    return date1 > date2
                }
        }
    }
    
    func formattedDuration() -> String {
        let minutes = Int(duration) / 60
        let seconds = Int(duration) % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }
}
