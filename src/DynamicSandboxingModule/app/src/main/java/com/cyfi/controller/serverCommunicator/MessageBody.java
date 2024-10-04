package com.cyfi.controller.serverCommunicator;

import java.util.HashMap;

public class MessageBody {
    public boolean success;
    public String command;
    public HashMap<String, String> contents;

    public MessageBody(boolean success) {
        this.success = success;

    }

    public MessageBody(boolean success, String command){
        this.success = success;
        this.command = command;
    }

    public MessageBody(boolean success, String command, HashMap<String, String> contents){
        this.success = success;
        this.command = command;
        this.contents = contents;
    }

    @Override
    public String toString(){
        return this.command + " " + this.contents;

    }

}
