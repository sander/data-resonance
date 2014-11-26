#include <FeedbackServo.h>
#include <Servo.h>

#define M0_MIN 9
#define M0_MAX 55
#define M1_MIN 5
#define M1_MAX 61
#define DEBUG false

const int n = 2;
FeedbackServo servo[n];

const int pressure_pin = A1;

int last_pressure;
unsigned long last_sunk;
unsigned long last_raised;

byte state[] = {0, 0, 0};

int read_index = 0;

void setup() {
  Serial.begin(115200);

  servo[0].begin(10, A2);
  servo[1].begin(9, A3);
  servo[1].setReversed(true);

  servo[0].set(M0_MIN);
  servo[1].set(M1_MIN);
}

void loop() {
  read_input();
  adjust_motors_to_pressure();
  loop_motors();
  if (update_state()) send_state();
}

void read_input() {
  while (Serial.available() > 0) {
    int val = Serial.read();
    if (val == 255) { read_index = 0; }
    else { servo[read_index].adjust(val); }
  }
}

void adjust_motors_to_pressure() {
  int val = pressure();
  int set0 = servo[0].setting();
  int set1 = servo[1].setting();
  if ((val > required_sink_pressure()) && (millis() - last_sunk > sink_timeout())) {//SINK_TIMEOUT)) {
    if (set0 < M0_MAX) { servo[0].set(set0 + 1); }
    if (set1 < M1_MAX) { servo[1].set(set1 + 1); }
    last_sunk = millis();
  } 
  else if ((val < required_static_pressure()) && (millis() - last_raised > raise_timeout())) {
    if (set0 > M0_MIN) { servo[0].set(set0 - 1); }
    if (set1 > M1_MIN) { servo[1].set(set1 - 1); }
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

int pressure() { return 255 - constrain(map(analogRead(pressure_pin), 128, 1023, 0, 254), 1, 255); }

int required_sink_pressure() { return (servo[0].setting() > 30) ? 210 : 150; }
int required_static_pressure() { return 50; }

int sink_timeout() { return (servo[0].setting() > 30) ? 10 : 5; }
int raise_timeout() { return 2; }


