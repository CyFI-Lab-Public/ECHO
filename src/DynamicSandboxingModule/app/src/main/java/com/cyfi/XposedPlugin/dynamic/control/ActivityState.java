package com.cyfi.XposedPlugin.dynamic.control;


import android.app.Activity;
import android.view.View;

import com.cyfi.XposedPlugin.dynamic.UIAutoMater.UIActionUtils;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActivityState {
    private String activityName;
    private String activityClass;
    private String activityHashCode;
    private WeakReference<Activity> activityWeakReference;
    boolean shouldHaveClickableViews;


    private final Map<WeakReference<View>, Boolean> viewClickedMap;  // all views that is clickable
//    private boolean visited;

    public ActivityState(Activity activity){ // by default, init all clickable views
        this(activity, true);
    }

    public ActivityState(Activity activity, boolean initClickableViews){
        this.activityName = activity.toString();
        this.activityClass = activity.getClass().getName();
        this.activityHashCode = Integer.toHexString(activity.hashCode());
        this.activityWeakReference = new WeakReference<Activity>(activity);
        this.viewClickedMap = new HashMap<>();
        this.shouldHaveClickableViews = initClickableViews;
        if(shouldHaveClickableViews){
            List<View> clickableViews = UIActionUtils.getAllClickableViews(activity);
            for(View view: clickableViews) {
                viewClickedMap.put(new WeakReference<View>(view), false);  // all views that is clickable
            }
        }
    }



//
//    public ActivityState(String activityName, ArrayList<String> viewList){
//        this.activityName = activityName;
//
//        viewClickedMap = new HashMap<View, Boolean>();
//        for(String str: viewList){
//            viewClickedMap.put(str, false);
//        }
//    }

    public String getActivityName(){
        return this.activityName;
    }

    public void setActivityName(String an){
        this.activityName = an;
    }

    public Map<WeakReference<View>, Boolean> getViewClickedMap(){
        return viewClickedMap;
    }

    public View getNextAvailableView(){
        for(Map.Entry<WeakReference<View>, Boolean> entry :this.viewClickedMap.entrySet()){
            View view = entry.getKey().get();
            if(view != null &&  view.isShown() && !entry.getValue()) {// valid, showing, not clicked
                return view;
            }
        }
        return null;
    }

    public boolean isViewExist(View view){
        for(WeakReference<View> viewWF: this.viewClickedMap.keySet()){
            if(viewWF.get() == view){
                return true;
            }
        }
        return false;
    }


    public boolean isViewClicked(View view) throws NullPointerException{
        for(WeakReference<View> viewWF: this.viewClickedMap.keySet()){
            if(viewWF.get() == view){
                Boolean res = this.viewClickedMap.get(viewWF);
                return res != null && res;
            }
        }
        this.viewClickedMap.put(new WeakReference<View>(view), false);
        return false;

      }

    public void setViewClicked(View view){
        for(WeakReference<View> viewWF: this.viewClickedMap.keySet()){
            if(viewWF.get() == view){
                this.viewClickedMap.put(viewWF, true);
            }
        }
        this.viewClickedMap.put(new WeakReference<View>(view), true);
    }


    public void updateWithActivity(Activity activity){
        if(activity != null && this.shouldHaveClickableViews)
            this.updateActivityStateWithNewViewList(UIActionUtils.getAllClickableViews(activity));
    }

    public void updateActivityStateWithNewViewList(List<View> viewList){
        for(View view: viewList) {
            boolean shouldAdd = true;

            //check if the view exists in the map
            for(WeakReference<View> viewWF: this.viewClickedMap.keySet()){
                if(viewWF.get() == view){
                    shouldAdd = false;
                    break;
                }
            }
            if(shouldAdd){
                this.viewClickedMap.put(new WeakReference<View>(view), false);
            }
        }
    }

    public Activity getActivity(){
        if (this.activityWeakReference != null) {
            return this.activityWeakReference.get();
        }else {
            return null;
        }

    }

}
