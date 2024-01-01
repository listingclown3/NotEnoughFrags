@echo off
color 2
title NotEnoughFrags
:StartServer
echo Starting up: NotEnoughFrags Testing Server
java -Xmx1024M -jar server.jar -o true
echo (%time%) Server closed/crashed... restarting!
goto StartServer