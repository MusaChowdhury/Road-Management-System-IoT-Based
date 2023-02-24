#include <ESP8266WiFi.h>
#include <WiFiUdp.h>


int UDP_PORT = 4000;
int Server_Port = 3000;
 
WiFiUDP UDP;

char packet[255];

  
void setup() {
  Serial.begin(19200);
  UDP.begin(UDP_PORT); 
}
 
void loop() {

  // command handeling  START
   String command = "";
   if(Serial.available())
   {
      command = Serial.readString();
   }
   
   if(command.indexOf("send") >= 0)
   {
      String server = command.substring(command.indexOf("_")+1,command.indexOf("+"));
      String data = command.substring(command.indexOf("+")+1,command.indexOf("#")); 
      sendToServer(server,data);
      
   }
   else  if (command.indexOf("wifi") >= 0 && command.indexOf("status") < 0)
   {
      
      String wName = command.substring(command.indexOf("_")+1,command.indexOf("+"));
      String pass = command.substring(command.indexOf("+")+1,command.indexOf("#"));
      connectWifi(wName,pass);
   }
   else if ( command.indexOf("wifi") >= 0 && command.indexOf("status") >= 0 )
   {
      Serial.println(checkWifi());
      
   }
  // command handeling  END


  // wifi read START
  int packetSize = UDP.parsePacket();
  if (packetSize) {
    int len = UDP.read(packet, 255);
    if (len > 0)
    {
      packet[len] = '\0';
    }
    Serial.println(packet);
  }
  // wifi read END
}

void connectWifi(String wName,String pass)
{
  //wifi_NAME+PASSWORD#
  WiFi.disconnect();
  WiFi.begin(wName, pass);
}

int checkWifi()
{
  //wifi_status
  return WiFi.status() == WL_CONNECTED;
}

void sendToServer(String server,String data)
{
  //send_IP+DATA#
  UDP.beginPacket((char*)server.c_str(), Server_Port);
  UDP.println(data);
  UDP.endPacket();

}