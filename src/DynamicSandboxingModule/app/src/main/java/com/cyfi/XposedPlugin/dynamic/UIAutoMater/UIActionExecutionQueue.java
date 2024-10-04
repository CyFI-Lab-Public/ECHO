package com.cyfi.XposedPlugin.dynamic.UIAutoMater;

import android.util.Log;

import com.cyfi.XposedPlugin.dynamic.DynamicAnalysisManager;

import java.util.concurrent.LinkedBlockingDeque;

import de.robv.android.xposed.XposedBridge;


public class UIActionExecutionQueue {
    private UIManageThread manageThread;
    private final LinkedBlockingDeque<UIAction> actionQueue;


    public UIActionExecutionQueue(){
        manageThread = new UIManageThread("UIAutomaterThread");
        actionQueue = new LinkedBlockingDeque<>();
        manageThread.start();
    }


    private static class UIActionManagerInstance{
        static UIActionExecutionQueue instance = new UIActionExecutionQueue();
    }


    public static UIActionExecutionQueue getInstance(){
        return UIActionManagerInstance.instance;
    }


    public void execute(UIAction action){
        synchronized (actionQueue){
            actionQueue.add(action);
            actionQueue.notify();
        }
    }

    public boolean isQueueEmpty(){
        return actionQueue.isEmpty();

    }

    public Thread.State getThreadState(){
        //TODO: check thread state
       return this.manageThread.getState();
    }


    private class UIManageThread extends Thread{

        UIManageThread(String name){
            super(name);
        }

        @Override
        public void run(){
//            XposedBridge.log("UIManager start run");
            while(true){
                synchronized (actionQueue){
                    while(actionQueue.isEmpty()){
                        try{
                            actionQueue.wait();
                        }catch(InterruptedException e){
                            Log.d("UIActionManager", "Manage thread interrupted");
                        }
                    }
                    try {
                        UIAction action = actionQueue.peek();
                        if(action != null) {
                            if(!DynamicAnalysisManager.getInstance().getInWebViewJSInterfaceTesting()){
                                XposedBridge.log("Action received: " + action.toString());
                                action.run();
                                actionQueue.remove(action);
                                action.uiActionDoneInterface.onActionFinished(action);
                            }
                        }

                    }catch(RuntimeException e){
                        e.printStackTrace();
                    }
                }
            }

        }
    }
}
