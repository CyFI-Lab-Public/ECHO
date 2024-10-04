package com.cyfi.XposedPlugin.dynamic.control;

import android.app.Activity;
import android.view.View;

import com.cyfi.XposedPlugin.dynamic.UIAutoMater.UIAction;
import com.cyfi.XposedPlugin.dynamic.UIAutoMater.UIActionDoneInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import de.robv.android.xposed.XposedBridge;

public class ActivityStateManager {
    private final Map<String, ActivityState> activityStateMap;
    private Stack<String> runTimeActivityStack;
    private String currentActivityName;
    private String rootActivityName;

//    private int actionPointer;
    private static final int ACTION_DELAY_MILLIS = 3000;



    public ActivityStateManager(){
        currentActivityName = "";
        rootActivityName = "";
        activityStateMap = new HashMap<>();
        runTimeActivityStack = new Stack<>();
    }

    public void addOrUpdateActivityState(Activity activity){
        if(activityStateMap.containsKey(activity.toString()) && activityStateMap.get(activity.toString()) != null) {
            ActivityState as = activityStateMap.get(activity.toString());
            if(as != null)
                as.updateWithActivity(activity);
        } else {
            boolean sameClassNameActivityExist = containActivityWithSameClassName(activity.getClass().getName());
            if(sameClassNameActivityExist){
                XposedBridge.log("ActivityStateManager: addOrUpdateActivityState: same class name activity exist");
            }
            ActivityState activityState = new ActivityState(activity, !sameClassNameActivityExist);
            activityStateMap.put(activity.toString(), activityState);
        }
    }


    public void activityOnCreate(Activity activity){
        addOrUpdateActivityState(activity);
        if(runTimeActivityStack.empty()) this.setRootActivityName(activity.toString());
        runTimeActivityStack.push(activity.toString());
        XposedBridge.log(activity.toString() + " added, current Stack size: " + runTimeActivityStack.size());


    }

    public void activityOnDestroy(Activity activity){
        if(!runTimeActivityStack.contains(activity.toString())){
            return;
        }
        boolean destroyedActivityPoped = false;

        while(!runTimeActivityStack.empty() && !destroyedActivityPoped){
            String topActivityName = runTimeActivityStack.peek();
            if(topActivityName.equals(activity.toString())){
                runTimeActivityStack.pop();
                destroyedActivityPoped = true;
            } else {
                runTimeActivityStack.pop();
            }
        }
        if(!runTimeActivityStack.empty()){
            this.currentActivityName = runTimeActivityStack.peek();
        }else{
            this.currentActivityName = null;
        }
        XposedBridge.log(activity.toString() + " destroyed, current Stack size: " + runTimeActivityStack.size());
    }

    public boolean hasActivityState(String name){
        return activityStateMap.containsKey(name);
    }

    public boolean containActivityWithSameClassName(String activityClassName){
        for(String name: activityStateMap.keySet()){
            if(name.startsWith(activityClassName)) return true;
        }
        return false;
    }

    public ActivityState getActivityStateByName(String name){
        return activityStateMap.get(name);
    }


    public void setCurrentActivityName(String currentActivityName) {
        this.currentActivityName = currentActivityName;
    }


    public UIAction getNextAction(UIActionDoneInterface actionDoneInterface){
        try{
            ActivityState as = this.getCurrentActivityState();
            View view =  as.getNextAvailableView();
            if(view == null) {
                if(as.getActivityName().equals(rootActivityName)) return null;
                else return new UIAction(ACTION_DELAY_MILLIS, as.getActivity(), actionDoneInterface);
            }
            else {
                as.setViewClicked(view);
                return new UIAction(view, ACTION_DELAY_MILLIS, actionDoneInterface);
            }
        } catch(NullPointerException nullPointerException) {
            return null;
        }


    }

    public String getCurrentActivityName(){
        if(this.runTimeActivityStack.empty()) return null;

        if(!this.currentActivityName.equals(this.runTimeActivityStack.peek())) this.currentActivityName = this.runTimeActivityStack.peek();

        ActivityState as = this.activityStateMap.get(this.currentActivityName);

        if(as != null && as.getActivity() != null ){
            return as.getActivityName();
        } else{
            // something bad happened, find the current available one
            for(String  activityName: this.activityStateMap.keySet()){
                ActivityState activityState = this.activityStateMap.get(activityName);
                if(as == null) continue;
                Activity activity = as.getActivity();
                if(activity.hasWindowFocus()) return activityName;
            }
        }
        // Still nothing works, as no activity has focus, just use the default one
        return currentActivityName;
    }

    public ActivityState getCurrentActivityState(){
        String currentActivityName = this.getCurrentActivityName();
        if(currentActivityName == null) return null;
        return this.activityStateMap.get(currentActivityName);

    }

    public void setRootActivityName(String ra){
        this.rootActivityName = ra;
    }

    public String getRootActivityName() {
        return rootActivityName;
    }


}
