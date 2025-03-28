import cv2
from fer import FER

def detect_emotion():
    detector = FER()
    cap = cv2.VideoCapture(0)  # Open the camera
    ret, frame = cap.read()  # Capture a frame
    cap.release()  # Release the camera

    if ret:
        emotions = detector.detect_emotions(frame)
        if emotions:
            emotion_data = emotions[0]  # Get first detected face
            dominant_emotion = max(emotion_data["emotions"], key=emotion_data["emotions"].get)
            return dominant_emotion
        return "No face detected"
    return "Error capturing frame"
