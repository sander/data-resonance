#include <FeedbackServo.h>
#include <Servo.h>

#define M0_DOWN 68
#define M0_UP 125
#define M1_DOWN 114
#define M1_UP 160

#define DEBUG true

const int n = 2;
FeedbackServo servo[n];

const int pressure_pin_1 = A1;
const int pressure_pin_2 = A0;

int last_pressure;
unsigned long last_sunk;
unsigned long last_raised;

byte state[] = {0, 0, 0};

int read_index = 0;

void setup() {
  Serial.begin(115200);

  servo[0].begin(5, A3);
  servo[1].begin(6, A2);
  servo[0].setMaxDelay(300);
  servo[1].setMaxDelay(300);
  servo[0].mconstrain(M0_DOWN, M0_UP);
  servo[1].mconstrain(M1_DOWN, M1_UP);
  //servo[1].setReversed(true);

  servo[0].set(M0_UP);
  servo[1].set(M1_UP);
}

void loop() {
  read_input();
  adjust_motors_to_pressure();
  loop_motors();
  //if (update_state()) send_state();
  Serial.println(pressure_original());
  delay(100);
}

void read_input() {
  while (Serial.available() > 0) {
    int val = Serial.read();
    if (val == 255) { read_index = 0; }
    else { servo[read_index++].adjust(val - 127); }
  }
}

void adjust_motors_to_pressure() {
  int val = pressure_original();
  int set0 = servo[0].setting();
  int set1 = servo[1].setting();
  if ((val < required_sink_pressure()) && (millis() - last_sunk > sink_timeout())) {//SINK_TIMEOUT)) {
    if (set0 > M0_DOWN) { servo[0].set(set0 - 1); }
    if (set1 > M1_DOWN) { servo[1].set(set1 - 1); }
    last_sunk = millis();
  } 
  else if ((val > required_static_pressure()) && (millis() - last_raised > raise_timeout())) {
    if (set0 < M0_UP) { servo[0].set(set0 + 1); }
    if (set1 < M1_UP) { servo[1].set(set1 + 1); }
    last_raised = millis();
  }
  last_pressure = val;
}

void loop_motors() {
  servo[0].loop();
  servo[1].loop();
}

boolean update_state() {
  byte s0 = servo[0].adjustedSetting();
  byte s1 = servo[1].adjustedSetting();
  byte s2 = last_pressure;
  if (state[0] != s0 || state[1] != s1 || state[2] != s2) {
    state[0] = s0;
    state[1] = s1;
    state[2] = s2;
    return true;
  } else {
    return false;
  }
}

void send_state() {
  write(0);
  for (int i = 0; i < 3; i++)
    write(state[i]);
  Serial.flush();
}

void write(byte b) { DEBUG ? Serial.println(b) : Serial.write(b); }

int pressure_original() {
  int p1 = analogRead(pressure_pin_1);
  int p2 = analogRead(pressure_pin_2);
  /*
  Serial.println();
  Serial.println(p1);
  Serial.println(p2);
  Serial.println(p1 + p2);
  delay(50);
  */
  return p1 + p2;
}

int pressure() { return 255 - constrain(map(pressure_original(), 1200, 2048, 0, 254), 1, 255); }

int required_sink_pressure() { /*return (servo[0].setting() > servo_threshold()) ? 140 : 70;*/ return 1900; }
int required_static_pressure() { /*return 50;*/ return 1750; }

int sink_timeout() { return (servo[0].setting() > servo_threshold()) ? 5 : 10; }
int raise_timeout() { return 2; }

int servo_threshold() { return 50; }
