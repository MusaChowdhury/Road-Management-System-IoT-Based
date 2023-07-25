#include <SoftwareSerial.h>

SoftwareSerial esp(2,3);

//Debug Mode
bool debug = true;

//Network Info
String ID = "1"; //ID of Controller. Must Be Uniqe for Each Controller. (IMPORTANT)
String wifiName = "test"; // WiFi Name
String password = "test"; // Password of the WiFi
String serverIP = "192.168.1.1"; // Ip Address of The Server. (Java Server)


//Sensor PIN Declaration
int userRead = 8;
int obstacleRead = 9;
int sunRead = A0;


//Output PIN Declaration
int roadLight = 5; // PWM IS NEDDED TO APPLY FADING EFFECT
int navigationLight = 6; 

//Sensitivity 
int sunLightAmount = 80;
int roadLightOutput = 70; //For Medium Output
int navigationDelay = 5000;
int uiDelay = 10000;

//Concurrent  Flag
long lastExecuted = 0;
long lastRequest = 0;
bool navigationFlag = false;
int userFlag = 0;

void setup() {
  delay(5000);
  //Sensor PIN Setup
  pinMode(userRead,INPUT);
  pinMode(obstacleRead,INPUT);
  pinMode(sunRead,INPUT);

  //Indicator Light Setup & Declaration
  pinMode(13,OUTPUT);
  pinMode(12,OUTPUT);
  digitalWrite(13,LOW);

  //Setting Up Serial Comunication both for ESP-01 and Arduino Itself
  if(debug) Serial.begin(9600);
  esp.begin(19200);
  
  //Initializing Indicator Lights
  digitalWrite(13,HIGH);
  digitalWrite(12,LOW);


  //Output PIN Setup
  pinMode(roadLight,OUTPUT);
  pinMode(navigationLight,OUTPUT);
  pinMode(4,OUTPUT); // To Provide GROUND To "roadLight" Pin
  pinMode(7,OUTPUT); // To Provide GROUND To "navigationLight" Pin

  //Initializing OUTPUT Pins
  digitalWrite(4,LOW);
  digitalWrite(7,LOW);


  //Connecting & Checking, Wifi & Server Connection
  bool flag = true;

  
  while(flag)
  {
    //esp.readString();
    digitalWrite(13,HIGH);
    if(debug) Serial.println("Inside Wifi Loop");
    esp.println("wifi_"+wifiName+"+"+password+"#");
    delay(10000);
    esp.println("wifi_status");
    delay(3000);
    String command = "";
    if (esp.available())
    {
        command = esp.readString();
    }
    if(debug) Serial.println("Responce Form Esp about wifi = "+ command); 
    if (command.indexOf("1") >= 0)
    {
      flag = false;
    }
     delay(3000);
    command = "";
    if (esp.available())
    {
        command = esp.readString();
    }
    if(debug) Serial.println("Responce Form Esp about wifi = "+ command); 
    if (command.indexOf("1") >= 0)
    {
      flag = false;
    }
    digitalWrite(13,LOW);
    delay(300);
    esp.flush();
    
  }
  digitalWrite(13,LOW);
  delay(1000);
  esp.flush();
  flag = true;
  while(flag)
  {
    esp.readString();
    digitalWrite(12,HIGH);
    if(debug)Serial.println("Inside Server Loop");
    esp.println("send_"+serverIP+"+?#");
    delay(3000);
    String command = esp.readString();
    if(debug) Serial.println("Responce Form Esp about Server = "+ command); 
    if(command.indexOf("ok") >= 0)
    {
      flag = false;
    }
    digitalWrite(12,LOW);
    delay(300);
  }
  digitalWrite(13,LOW);
  digitalWrite(12,LOW);
  

}


void loop(){
  String command = "";
  int obstacleStatus = digitalRead(obstacleRead);
  int sunStatus = analogRead(sunRead);
  int userStatus = digitalRead(userRead);

  
  if(debug)
  {
    Serial.println("Obstacle = " + String(obstacleStatus) + " Sun = " + String(sunStatus) + " User = " + String(userStatus));
  }
  
  if ((sunStatus <= sunLightAmount) && !(obstacleStatus == 0))
  {
    if (debug) Serial.println("Road Light To Medium");
   analogWrite(roadLight,roadLightOutput);
  }
  else if ( ( sunStatus <= sunLightAmount) && (obstacleStatus == 0))
  {
    analogWrite(roadLight,255);
    if (debug) Serial.println("Road Light Maximum");
  }
  else
  {
    analogWrite(roadLight,0);
    if (debug) Serial.println("Road Light Off");
  }
  
  if(obstacleStatus == 0)
  {
    esp.println("send_"+serverIP+"+"+ID+"-n#");
    if (debug) Serial.println("Request for Navigation is Sent" );
    delay(2000);
    esp.flush();
  }
  
  if(userStatus == 1)
  {
    esp.println("send_"+serverIP+"+"+ID+"-r#");
    if (debug) Serial.println("Request for Emergency is Sent" );
    delay(1000);
    esp.flush();
  }
 
  if(esp.available())
  {
    command = esp.readString();
    command = command.substring(command.indexOf("@")+1,command.indexOf("#"));
    if (debug) Serial.println("____Received -> "+ command);
    delay(100);
  }

  if(command.charAt(0) == 'n' && (command.indexOf(ID) > 0))
  {
    digitalWrite(navigationLight,HIGH);
    if (debug) Serial.println("Navigation Light On");
    lastExecuted = millis();
    navigationFlag = true;   
  }
  
  if((command.indexOf("R") >= 0) || (command.indexOf("C") >= 0))
  {
    if (debug) Serial.println("Inside Request Handler");
  
    if(command.charAt(0) == 'R')
    {
        esp.println("send_"+serverIP+"+"+ID+"-R#");
        if (debug) Serial.println("Request Received");
        userFlag = 1;
    }
    else if (command.charAt(0) == 'C')
    {
        esp.println("send_"+serverIP+"+"+ID+"-D#");
        if (debug) Serial.println("Request Processed");
        userFlag = 2;
        lastRequest = millis();
    }
  }
      
  if( ((millis()-lastExecuted) > navigationDelay) && navigationFlag)
  {
     if (debug) Serial.println("Navigation Light Off");
     digitalWrite(navigationLight,LOW);
     navigationFlag = false;
  }
  
  if(userFlag == 0)
  {
     if (debug) Serial.println("Emergency LED Turned Off");
     digitalWrite(13,LOW);
     digitalWrite(12,LOW); 
  }
  else if (userFlag == 1)
  {
    if (debug) Serial.println("Emergency Request LED Turned On");
    digitalWrite(13,HIGH);
    digitalWrite(12,LOW);
  }
  else
  {
    if (debug) Serial.println("Emergency Processed LED Turned On");
    digitalWrite(13,LOW);
    digitalWrite(12,HIGH);
    if(millis()-lastRequest > uiDelay)
    {
       userFlag = 0; 
    }
  }
  
  esp.flush();
  Serial.flush();
  delay(100);
  

}




  