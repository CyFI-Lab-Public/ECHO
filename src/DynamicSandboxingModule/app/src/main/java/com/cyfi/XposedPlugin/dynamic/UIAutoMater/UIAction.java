package com.cyfi.XposedPlugin.dynamic.UIAutoMater;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import de.robv.android.xposed.XposedBridge;


public class UIAction implements Runnable {
    public Activity activity;
    public View view;
    public ActionType actionType;
    public int delayInMilli;
    public String content;
    public UIActionDoneInterface uiActionDoneInterface;


    public UIAction(int delay, Activity activity, UIActionDoneInterface actionDoneInterface){
        this.activity = activity;
        this.actionType = ActionType.Activity_Finish;
        this.uiActionDoneInterface = actionDoneInterface;
        this.delayInMilli = delay;
    }

    public UIAction(View clickable, int delay, UIActionDoneInterface actionDoneInterface){
        this.view = clickable;
        this.delayInMilli = delay;
        actionType = ActionType.Button_Click;
        this.uiActionDoneInterface = actionDoneInterface;

    }

    public UIAction(TextView textView, String text, int delay, UIActionDoneInterface actionDoneInterface){
        this.view = textView;
        this.content = text;
        this.delayInMilli = delay;
        this.actionType = ActionType.Text_Set;
        this.uiActionDoneInterface = actionDoneInterface;

    }

    @Override
    public void run() {
        try {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            Runnable myRunnable = new Runnable() {
                @Override
                public void run() {
                    performAction();
                } // This is your code
            };
            mainHandler.post(myRunnable);
            Thread.sleep(this.delayInMilli);
        }catch(InterruptedException e){
            e.printStackTrace();
        }
    }

    private void performAction(){
        if(this.actionType == ActionType.Activity_Finish){
            if(this.activity != null){
                try{
                    activity.finish();
                }catch(Exception e){
                    XposedBridge.log("finish Exception: " + e.toString());
                    XposedBridge.log(e.getLocalizedMessage());
                }
                return;
            }
        }
        else if(view == null){
            XposedBridge.log("null view, not to execute");
            return;
        }
        switch(this.actionType){
            case Button_Click:
                view.performClick();
                break;
            case Text_Set:
                ((TextView)view).setText(this.content);
                break;
            default:
                break;
        }
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder();
        sb.append("UIAction: ");
        sb.append("actionType: " + this.actionType);
        if(this.view != null){
            if(this.view instanceof Button){
                sb.append(" button: " + ((Button)this.view).getText());
            }
        }
        if(this.activity != null){
            sb.append(" activity: " + this.activity.toString());
        }
        sb.append(" delay: " + this.delayInMilli);

        return sb.toString();
    }

}
