#include <FeedbackServo.h>
#include <Servo.h>

#define FINAL
//#define TOUCH

#ifdef FINAL
#define PRESSURE_L A1
#define PRESSURE_R A0
#define POT_L A3
#define POT_R A2
#define SERVO_L 5
#define SERVO_R 6
#define UP_L 50
#define UP_R 50
#define DOWN_L 130
#define DOWN_R 130
#define MIN_L 0
#define MAX_L 180
#define MIN_R 0
#define MAX_R 180
#define MAX_DELAY 300
#else
#define PRESSURE_L A1
#define PRESSURE_R A1
#define POT_L A2
#define POT_R A3
#define SERVO_L 10
#define SERVO_R 9
#define MIN_L 0
#define MAX_L 180
#define MIN_R 0
#define MAX_R 180
#define MAX_DELAY 300
#endif

#define SEND_IVAL 50

// #define pins with ifdefs for old/new prototype

int pressureL;
int pressureR;

int targetL = 0;
int targetR = 0;

int touchTargetL = UP_L;
int touchTargetR = UP_R;

FeedbackServo servoL;
FeedbackServo servoR;

unsigned long lastSunk;
unsigned long lastRaised;
unsigned long lastSent;

void setup() {
  Serial.begin(115200);
  
  lastSunk = lastRaised = lastSent = 0;
  
  servoL.begin(SERVO_L, POT_L);
  servoL.setMaxDelay(MAX_DELAY);
  servoL.mconstrain(MIN_L, MAX_L);
  servoR.begin(SERVO_R, POT_R);
  servoR.setMaxDelay(MAX_DELAY);
  servoR.mconstrain(MIN_R, MAX_R);
  servoR.setReversed(true);
  
  establish();
}

void loop() {
  update();
  
  servoL.loop();
  servoR.loop();
  
  if (Serial.available() > 0) {
    read();
  } else if (millis() - lastSent > SEND_IVAL) {
    send();
    lastSent = millis();
  }
}

void establish() {
  while (Serial.available() <= 0) {
    Serial.println("?");
    delay(300);
  }
  Serial.println("!");
}

void send() {
  Serial.print('V');
  Serial.print(pressureL);
  Serial.print(',');
  Serial.print(pressureR);
  Serial.print(',');
  Serial.print(servoL.feedback());
  Serial.print(',');
  Serial.print(servoR.feedback());
  Serial.print(',');
  Serial.print(servoL.attached());
  Serial.print(',');
  Serial.print(servoR.attached());
  Serial.println();
}

void read() {
  int nTargetL = Serial.parseInt();
  int nTargetR = Serial.parseInt();
  if (Serial.read() == '\n') {
    targetL = nTargetL;
    targetR = nTargetR;
  }
}

void update() {
  pressureL = analogRead(PRESSURE_L);
  pressureR = analogRead(PRESSURE_R);
  
#ifdef TOUCH
  adjustMotorsToPressure();
  servoL.set(max(targetL, touchTargetL));
  servoR.set(max(targetR, touchTargetR));
#else
  servoL.set(targetL);
  servoR.set(targetR);
#endif
  
  /*
  adjustMotorsToPressure();
  servoL.adjust(targetL);
  servoR.adjust(targetR);
  */
}

void adjustMotorsToPressure() {
  if (shouldSink()) {
    sink();
  } else if (shouldRaise()) {
    raise();
  }
}

boolean shouldSink() {
  return (min(pressureL, pressureR) < requiredSinkPressure()) && (millis() - lastSunk > sinkTimeout());
}

void sink() {
  if (touchTargetL < DOWN_L) { touchTargetL += sinkStep(); }
  if (touchTargetR < DOWN_R) { touchTargetR += sinkStep(); }
  lastSunk = millis();
}

boolean shouldRaise() {
  return (min(pressureL, pressureR) > requiredStaticPressure()) && (millis() - lastRaised > raiseTimeout());
}

void raise() {
  if (touchTargetL > UP_L) { touchTargetL += raiseStep(); }
  if (touchTargetR > UP_R) { touchTargetR += raiseStep(); }
  lastRaised = millis();
}

int requiredStaticPressure() {
#ifdef FINAL
  return 1000;
#else
  return 830;
#endif
}

int requiredSinkPressure() {
  return 850;
}

int sinkTimeout() {
  return 5;
}

int raiseTimeout() {
  return 10;
}

int sinkStep() {
  return 1;
}

int raiseStep() {
  return -1;
}
