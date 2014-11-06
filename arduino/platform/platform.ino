#include <Adafruit_MPR121.h>
#include <FeedbackServo.h>
#include <Servo.h>
#include <Wire.h>

const int n = 2;
FeedbackServo servo[n];

const int touch_pin = 4;
const int touch_start = 180;
const int touch_threshold = 20;

const int ir_led_pin = 13;
const int ir_sense_pin = A0;
const int ir_threshold = 20;

const int pressure_pin = A1;
const int pressure_threshold = 10;
const int pressure_none = 1023;
const int pressure_high = 128;

int i = 0;

Adafruit_MPR121 cap = Adafruit_MPR121();

void setup() {
  Serial.begin(115200);
  
  pinMode(ir_led_pin, OUTPUT);
  
  if (!cap.begin(0x5A)) {
    Serial.println("MPR121 not found, check wiring?");
    while (1);
  }

  servo[0].begin(10, A2);
  servo[1].begin(9, A3);
}

void loop() {
  while (Serial.available() > 0) {
    int val = Serial.read();
    if (val == 255) {
      i = 0;
    }
    else {
      servo[i].set(i == 0 ? val : 180 - val);
      i = (i + 1) % n;
    }
  }
  servo[0].loop();
  servo[1].loop();
  
  setTouch(cap.filteredData(touch_pin));
  setProximity(pulse());
  setPressure(pressure());
  sendStatus();
  delay(10);
}

int pulse() {
  // Adapted from Herman Aartsen's IRranger.pde.
  int iHpulse;
  int iLpulse;
  
  iLpulse = analogRead(ir_sense_pin); // measure low signal (=reference)
  digitalWrite(ir_led_pin, HIGH);     // turn the LED on (HIGH is the voltage level)
  delayMicroseconds(60);              // let signal settle
  iHpulse = analogRead(ir_sense_pin); // measure high signal
  digitalWrite(ir_led_pin, LOW);      // turn the LED on (HIGH is the voltage level)

  return iHpulse - iLpulse;
}

int pressure() {
  return analogRead(pressure_pin);
}

int last_pressure = 0;
void setPressure(int pressure) {
  if (pressure != last_pressure) {
    last_pressure = pressure;
  }
}

int last_touched = 0;
void setTouch(int touch) {
  if (touch != last_touched) {
    last_touched = touch;
  }
}

int last_proximity = 0;
void setProximity(int proximity) {
  if (abs(proximity - last_proximity) > ir_threshold) {
    last_proximity = proximity;
  }
}

const boolean debug = false;

int last_status = 0;
void sendStatus() {
  int proximity_constrained = 127 - constrain(map(last_proximity, 142, 354, 0, 126), 1, 127);
  write(0);
  write(last_touched);
  write(255 - constrain(map(last_pressure, 128, 1023, 0, 254), 1, 255));
  write(proximity_constrained);
  if (debug) {
    Serial.println();
    delay(500);
  }
}

void write(byte b) {
  if (debug)
    Serial.println(b);
  else
    Serial.write(b);
}
