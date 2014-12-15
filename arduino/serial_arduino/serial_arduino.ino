#include <Servo.h>

int pressureL;
int pressureR;
int motorL;
int motorR;

int targetL = 700;
int targetR = 700;

Servo servoL;
Servo servoR;

void setup() {
  Serial.begin(115200);
  
  servoL.attach(5);
  servoR.attach(6);
  
  establish();
}

void loop() {
  update();
  if (Serial.available() > 0) {
    int nTargetL = Serial.parseInt();
    int nTargetR = Serial.parseInt();
    if (Serial.read() == '\n') {
      targetL = nTargetL;
      targetR = nTargetR;
    }
  } else {
    send();
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
  Serial.print(motorL);
  Serial.print(',');
  Serial.print(motorR);
  Serial.println();
}

void update() {
  pressureL = analogRead(A1);
  pressureR = analogRead(A0);
  motorL = analogRead(A3);
  motorR = analogRead(A2);
  
  servoL.writeMicroseconds(targetL);
  servoR.writeMicroseconds(targetR);
}

