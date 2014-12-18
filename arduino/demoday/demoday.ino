#include <FeedbackServo.h>
#include <Servo.h>

#define FINAL

#ifdef FINAL
#define PRESSURE_L A1
#define PRESSURE_R A0
#define POT_L A3
#define POT_R A2
#define SERVO_L 5
#define SERVO_R 6
#define MIN_L 960
#define MAX_L 1248
#define MIN_R 960
#define MAX_R 1248
#define MAX_DELAY 500

#define MIN_L 700
#define MAX_L 2300
#define MIN_R 700
#define MAX_R 2300
#else
#define PRESSURE_L A1
#define PRESSURE_R A1
#define POT_L A2
#define POT_R A3
#define SERVO_L 10
#define SERVO_R 9
#define MIN_L 700
#define MAX_L 2300
#define MIN_R 700
#define MAX_R 2300
#define MAX_DELAY 150
#endif

#define SEND_IVAL 100

// #define pins with ifdefs for old/new prototype

int pressureL;
int pressureR;

int targetL = 0;
int targetR = 0;

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
  servoR.setReversed(false);
  
  establish();
}

void loop() {
  update();
  
  servoL.loop();
  servoR.loop();
  
  if (Serial.available() > 0) {
    read();
  } else if (millis() - lastSent > SEND_IVAL) {
    //send();
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
  
  
  //servoL.setMicro(targetL);
  //servoR.setMicro(targetR);
  
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
  int setL = servoL.settingMicro();
  int setR = servoR.settingMicro();
  if (setL < MAX_L) { servoL.setMicro(setL + sinkStep()); }
  if (setR < MAX_R) { servoR.setMicro(setR + sinkStep()); }
  lastSunk = millis();
}

boolean shouldRaise() {
  return (max(pressureL, pressureR) > requiredStaticPressure()) && (millis() - lastRaised > raiseTimeout());
}

void raise() {
  int setL = servoL.settingMicro();
  int setR = servoR.settingMicro();
  if (setL > MIN_L) { servoL.setMicro(setL - raiseStep()); }
  if (setR > MIN_R) { servoR.setMicro(setR - raiseStep()); }
  lastRaised = millis();
}

int requiredStaticPressure() {
#ifdef FINAL
  return 700;
#else
  return 830;
#endif
}

int requiredSinkPressure() {
  return 800;
}

int sinkTimeout() {
  return 0;
}

int raiseTimeout() {
  return 0;
}

int sinkStep() {
  return 1;
}

int raiseStep() {
  return 1;
}
