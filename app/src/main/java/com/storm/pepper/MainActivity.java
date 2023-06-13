package com.storm.pepper;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.aldebaran.qi.sdk.QiSDK;
import com.aldebaran.qi.sdk.design.activity.RobotActivity;
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy;
import com.aldebaran.qi.sdk.util.FutureUtils;
import com.storm.posh.plan.planelements.PlanElement;
import com.storm.posh.plan.planelements.Sense;
import com.storm.posh.plan.reader.xposh.XPOSHPlanReader;
import com.storm.posh.BaseBehaviourLibrary;
import com.storm.posh.Planner;
import com.storm.posh.plan.Plan;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class MainActivity extends RobotActivity implements PepperLog {

    private int mode = 0;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final SimpleDateFormat logTimeFormat = new SimpleDateFormat("HH:mm:ss.SSSS");

    private int maxIterations = 0;
    private boolean stopRunningPlan;

    private Planner planner;
//    private UIPlanTree uiPlanTree = null;
//    private ExecutorService backgroundColorExecutor = null;
//    private ScheduledExecutorService backgroundPingerScheduler;
//    private ConstraintLayout rootLayout = null;
//    private Handler generalHandler = null;
    private TextView plannerLog;
    private TextView checkedSenses;
    private TextView currentDriveName;
    private TextView currentElementName;
    public TextView locationsLabel;

    private TextView selectedPlan;

    private PepperServer pepperServer;
    private BaseBehaviourLibrary behaviourLibrary;

    public Button startButton;
    public Button stopButton;
    public Button selectPlan;
    public Button addLocationButton;
    public Button clearLocationsButton;

    private ArrayList currentElements = new ArrayList();

    ListView drivesList;
    ListView elementsList;

    DrivesListAdapter drivesAdapter;
    ElementsListAdapter elementsAdapter;
    NoElementsListAdapter noElementsAdapter;

    private int planResourceId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.OVERLAY);

//        plannerLog = findViewById(R.id.textPlannerLog);
//        currentDriveName = findViewById(R.id.currentDrive);
//        currentElementName = findViewById(R.id.currentElement);
//        checkedSenses = findViewById(R.id.checkedSenses);

//        rootLayout = findViewById(R.id.root_layout);

        // HOLLY FUCK: this constructor saves itself into a singleton variable which is used by the planner
        behaviourLibrary = new POSHBehaviourLibrary();
        // behaviourLibrary = new BenediktBehaviourLibrary();
        // end configure for chosen plan

        behaviourLibrary.setPepperLog(this);
        behaviourLibrary.setActivity(this);

        // initialize base POSH stuff
        // ACHTUNG: planner gets accoess to the behavior library through a singleton. Needs to be fixed.
        planner = new Planner(this);


        // Pepper server
        pepperServer = new PepperServer(this);


        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        // Register the RobotLifecycleCallbacks to this Activity.
        QiSDK.register(this, BaseBehaviourLibrary.getInstance());


        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        selectPlan = findViewById(R.id.select_plan);
        addLocationButton = findViewById(R.id.location_add);
        clearLocationsButton = findViewById(R.id.locations_clear);
        locationsLabel = findViewById(R.id.locations_label);

        selectedPlan = findViewById(R.id.label_selected_plan);

        startButton.setOnClickListener(ignore -> {
            Log.d(TAG, "Starting");
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            selectPlan.setEnabled(false);
            runPlan();
        });

        stopButton.setOnClickListener(ignore -> {
            appendLog(TAG, "STOPPING");
            stopRunningPlan = true;

            behaviourLibrary.stopMoving();
            new Thread(() -> {
                behaviourLibrary.removeListeners();
            }).start();

            behaviourLibrary.reset();

            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            selectPlan.setEnabled(true);
        });

        // set the initial state for the buttons
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        selectPlan.setEnabled(true);


        selectPlan.setOnClickListener(ignore -> {
            showPopupList();
        });

        addLocationButton.setOnClickListener(ignore -> {
            appendLog("SAVING?");
            behaviourLibrary.saveLocation();
        });

        clearLocationsButton.setOnClickListener(ignore -> {
            appendLog("CLEARING?");
            behaviourLibrary.clearLocations();
        });

        drivesList = (ListView) findViewById(R.id.drives_list);
        elementsList = (ListView) findViewById(R.id.elements_list);




        // configure for chosen plan
        //planResourceId = R.raw.plan;
        setSelectedPlan("Plan", R.raw.plan);
        //readPlan();
    }

    public void updateLocationsCount(int count) {
        runOnUiThread(() -> {
            locationsLabel.setText(getResources().getString(R.string.saved_locations_count, count));
        });
    }

    @Override
    public void appendLog(final String tag, final String message, boolean server) {
        Log.d(tag, message);
//        final Date currentTime = Calendar.getInstance().getTime();
        final String formattedMessage = String.format("%s|%s|%s", planner.getIteration(), tag, message);

        if (server) {
            pepperServer.sendMessage(formattedMessage);
        }
    }

    private void setSelectedPlan(String name, int ressourceId) {
        selectedPlan.setText(name);
        planResourceId = ressourceId;
        readPlan();
        appendLog(TAG, "Set Selected Plan: \"" + name + "\" with id: " + planResourceId);
    }

    private void showPopupList() {
        class PlanListItem {
            public final String name;
            public final int resourceId;
            public PlanListItem(String name, int resourceId) {
                this.name = name;
                this.resourceId = resourceId;
            }
        };

        final PlanListItem[] items = {
            new PlanListItem("Plan",                   R.raw.plan),
            new PlanListItem("Plan Benedikt",          R.raw.plan_benedikt),
            new PlanListItem("Plan Chain Actions",     R.raw.plan_chain_actions),
            new PlanListItem("Plan Check Watch",       R.raw.plan_check_watch),
            new PlanListItem("Plan Die",               R.raw.plan_die),
            new PlanListItem("Plan Drive Shake Hands", R.raw.plan_drive_shake_hands),
            new PlanListItem("Plan Matthias",          R.raw.plan_matthias),
            new PlanListItem("Plan Touch Wave",        R.raw.plan_touch_wave),
            new PlanListItem("Plan Working",           R.raw.plan_working)
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select a Plan");

        // UGLY HACK: map the names like this because streams are not available here yet
        final String[] names = new String[items.length];
        for(int i = 0; i < items.length; i++) {
            names[i] = items[i].name;
        }
        // UGLY^2: setItems wants CharSequence, so cannot use items directly...
        builder.setItems(names, (DialogInterface dialog, int which) -> {
            // HACK: this needs to be somewhere else
            planner.reset();
            setSelectedPlan(items[which].name, items[which].resourceId);
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    public void appendLog(String tag, String message) {
        this.appendLog(tag, message, true);
    }

    @Override
    public void appendLog(String message) {
        this.appendLog(TAG, message, true);
    }

    @Override
    public void clearLog() {
        runOnUiThread(new Runnable(){
            @Override
            public void run(){
//                plannerLog.setText("");
            }
        });
    }

    @Override
    public void checkedBooleanSense(String tag, Sense sense, boolean value) {
        String formattedMessage = String.format("%s: %b", sense, value);

        appendLog(tag, "Checked sense - "+formattedMessage, false);
        notifyABOD3(sense.getNameOfElement(), "S");

//        runOnUiThread(new Runnable(){
//            @Override
//            public void run(){
//                checkedSenses.append("\n" + formattedMessage);
//            }
//        });
    }

    @Override
    public void checkedDoubleSense(String tag, Sense sense, double value) {
        String formattedMessage = String.format("%s: %f", sense, value);

        appendLog(tag, "Checked sense - "+formattedMessage, false);
        notifyABOD3(sense.getNameOfElement(), "S");

//        runOnUiThread(new Runnable(){
//            @Override
//            public void run(){
//                checkedSenses.append("\n" + formattedMessage);
//            }
//        });
    }

    public void displaySenses() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                checkedSenses.setText("");
//                checkedSenses.append(String.format("Idle Time: %f\n\n", behaviourLibrary.getIdleTime()));
//
//                checkedSenses.append(String.format("Human Present: %b\n", behaviourLibrary.isHumanPresent()));
//                checkedSenses.append(String.format("Human Engaged: %b\n\n", behaviourLibrary.isHumanEngaged()));
//
//                checkedSenses.append(String.format("Mapping Complete: %b\n", behaviourLibrary.isMappingComplete()));
//                checkedSenses.append(String.format("Mapping In Progress: %b\n\n", behaviourLibrary.isMappingInProgress()));
//
//                checkedSenses.append(String.format("Battery Low: %b\n", behaviourLibrary.isBatteryLow()));
//                checkedSenses.append(String.format("Battery Charging: %b\n", behaviourLibrary.isBatteryCharging()));
            }
        });
    }

    @Override
    public void clearCheckedSenses() {
//        runOnUiThread(new Runnable(){
//            @Override
//            public void run(){
//                checkedSenses.setText("");
//            }
//        });
    }

    @Override
    public void addCurrentElement(final PlanElement element) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (element != null) {
                    currentElements.add(element);
                }
            }
        });
    }

    @Override
    public void clearCurrentElements() {
        currentElements.clear();
    }

    @Override
    public void notifyABOD3(String name, String type) {
        String message = String.format("ABOD3,%s,%s,%d", name, type, planner.getIteration());
//        this.appendLog(TAG, message, false);
        pepperServer.sendMessage(message);
    }

    public void readPlan(View view) {
        readPlan();
    }

    private void readPlan() {
        Log.d(TAG, "READING PLAN");

        Plan.getInstance().cleanAllLists();
        XPOSHPlanReader planReader = new XPOSHPlanReader();

        InputStream planFile = getResources().openRawResource(planResourceId);

        planReader.readFile(planFile);

        planner.start();

        displayPlan();
    }

    private void displayPlan() {
        Plan plan = Plan.getInstance();

        if (drivesAdapter == null || drivesAdapter.isStale(plan.getCurrentDrive())) {
            drivesAdapter = new DrivesListAdapter(this, plan.getDriveCollections(), plan.getCurrentDrive());
            drivesList.setAdapter(drivesAdapter);
        }

        if (currentElements.isEmpty()) {
            if (noElementsAdapter == null) {
                ArrayList noElement = new ArrayList();
                noElement.add("Performing no action...");
                noElementsAdapter = new NoElementsListAdapter(this, noElement);
            }
            elementsList.setAdapter(noElementsAdapter);
        } else {
//            Collections.reverse(currentElements);
            elementsAdapter = new ElementsListAdapter(this, currentElements);
            elementsList.setAdapter(elementsAdapter);
        }
    }

    public void runPlan() {
        clearLog();
        FutureUtils.wait(0, TimeUnit.SECONDS).andThenConsume(ignore -> behaviourLibrary.doHumans());

        stopRunningPlan = false;

        final Handler handler = new Handler();
        Runnable planRunner = new Runnable() {
            int iteration = 1;
            boolean completed = false;

            @Override
            public void run() {
                if (stopRunningPlan == true) {
                    appendLog("PLAN RUN STOPPED");
                    stopRunningPlan = false;
                    return;
                }

                try {
                    clearLog();
                    clearCurrentElements();
                    appendLog(" ");
                    appendLog(String.format("\n\n.... starting update #%d....\n\n", iteration));
                    completed = !planner.update(iteration);
                    displayPlan();

                } catch (Exception e) {
                    // TODO: handle exception
                }
                finally {
                    if (completed) {
                        appendLog("REACHED END OF PLAN");
                    } else if (maxIterations > 0 && iteration > maxIterations) {
                        appendLog("REACHED ITERATION LIMIT");
                    } else {
                        iteration += 1;

                        handler.postDelayed(this, 1000);
                    }
                }
            }
        };

        // runnable must be execute once
        FutureUtils
            .wait(1, TimeUnit.SECONDS)
            .andThenConsume(ignore -> {
                appendLog("RESETTING PLAN");
                planner.reset();

                appendLog("RUNNING PLAN");
                handler.postDelayed(planRunner, 1000);
            });
    }


    @Override
    protected void onDestroy() {
        // Unregister the RobotLifecycleCallbacks for this Activity.
        QiSDK.unregister(this, BaseBehaviourLibrary.getInstance());
        pepperServer.destroy();
        super.onDestroy();
    }
}
