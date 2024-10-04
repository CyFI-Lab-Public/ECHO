package com.cyfi.XposedPlugin.dynamic.UIAutoMater;

import android.app.Activity;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;


public class UIActionUtils {


    public static List<View> getViewsFromActivity(Activity activity) {

        View v = activity.getWindow().getDecorView();

        return getAllViews(v);
    }

    public static List<View> getAllViews(View parent) {
        if(parent == null) return null;
        View rootView = parent.getRootView();
//        Log.d("AUTOMATER","decor: " + parent.toString());
//        XposedBridge.log("decor: " + parent.toString());

        ArrayList<View> views = new ArrayList<View>();
        if (parent instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) parent).getChildCount(); i++) {
                View v = ((ViewGroup) parent).getChildAt(i);
                List<View> vr = getAllViews(v);
                views.addAll(vr);
            }
        } else {
            views.add(parent);
        }
        return views;
    }


    public static List<View> getAllClickableViews(Activity activity){
        return getAllClickableViews(activity.getWindow().getDecorView());
    }

    public static  List<View> getAllClickableViews(View parent){
        if(parent == null) return null;
        List<View> views = getAllViews(parent);
        ArrayList<View> clickableViews =new ArrayList<View>();
        for (View view: views){
            if(getOnClickListener(view) != null){
                clickableViews.add(view);
            }
        }
        return clickableViews;
    }


    public List<View> getAllButtons(View parent) {
        List<View> views = getAllViews(parent);
        ArrayList<View> buttons = new ArrayList<View>();
        for (View view : views) {
            if (view instanceof Button && Integer.valueOf(view.getId()) > 0) {
                buttons.add(view);
            }
        }
        return buttons;
    }

    public List<View> getAllTextViews(View parent) {
        List<View> views = getAllViews(parent);
        ArrayList<View> textViews = new ArrayList<View>();
        for (View view : views) {
            if (view instanceof TextView && Integer.valueOf(view.getId()) > 0) {
                textViews.add(view);
            }
        }
        return textViews;
    }


    public static boolean isThreadWaiting(){
        return UIActionExecutionQueue.getInstance().getThreadState().equals(Thread.State.WAITING);
    }

    public void finishActivity(Activity activity){
        activity.finish();
    }





    public static View.OnClickListener getOnClickListener(View view) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return getOnClickListenerV14(view);
        } else {
            return getOnClickListenerV(view);
        }
    }

    //Used for APIs lower than ICS (API 14)
    private static View.OnClickListener getOnClickListenerV(View view) {
        View.OnClickListener retrievedListener = null;
        String viewStr = "android.view.View";
        Field field;

        try {
            field = Class.forName(viewStr).getDeclaredField("mOnClickListener");
            retrievedListener = (View.OnClickListener) field.get(view);
        } catch (NoSuchFieldException ex) {
            Log.e("Reflection", "No Such Field.");
        } catch (IllegalAccessException ex) {
            Log.e("Reflection", "Illegal Access.");
        } catch (ClassNotFoundException ex) {
            Log.e("Reflection", "Class Not Found.");
        }

        return retrievedListener;
    }

    //Used for new ListenerInfo class structure used beginning with API 14 (ICS)
    private static View.OnClickListener getOnClickListenerV14(View view) {
        View.OnClickListener retrievedListener = null;
        String viewStr = "android.view.View";
        String lInfoStr = "android.view.View$ListenerInfo";

        try {
            Field listenerField = Class.forName(viewStr).getDeclaredField("mListenerInfo");
            Object listenerInfo = null;

            if (listenerField != null) {
                listenerField.setAccessible(true);
                listenerInfo = listenerField.get(view);
            }

            Field clickListenerField = Class.forName(lInfoStr).getDeclaredField("mOnClickListener");

            if (clickListenerField != null && listenerInfo != null) {
                retrievedListener = (View.OnClickListener) clickListenerField.get(listenerInfo);
            }
        } catch (NoSuchFieldException ex) {
            Log.e("Reflection", "No Such Field.");
        } catch (IllegalAccessException ex) {
            Log.e("Reflection", "Illegal Access.");
        } catch (ClassNotFoundException ex) {
            Log.e("Reflection", "Class Not Found.");
        }
        return retrievedListener;
    }

}
