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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.aldebaran.qi.sdk.QiSDK;
import com.aldebaran.qi.sdk.design.activity.RobotActivity;
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy;
import com.aldebaran.qi.sdk.util.FutureUtils;
import com.storm.posh.PlannerWithPriorities;
import com.storm.posh.plan.planelements.PlanElement;
import com.storm.posh.plan.planelements.Sense;
import com.storm.posh.plan.planelements.action.ActionEvent;
import com.storm.posh.plan.planelements.action.ActionPatternElement;
import com.storm.posh.plan.reader.xposh.XPOSHPlanReader;
import com.storm.posh.BaseBehaviourLibrary;
import com.storm.posh.plan.Plan;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class MainActivity extends RobotActivity implements PepperLog {

    private int mode = 0;
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final SimpleDateFormat logTimeFormat = new SimpleDateFormat("HH:mm:ss.SSSS");

    private PepperServer pepperServer;

    private int maxIterations = 0;
    private boolean stopRunningPlan;

    //private Planner planner;
    private PlannerWithPriorities planner;

    private BaseBehaviourLibrary behaviourLibrary;

    // initialize UI stuff

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

    public Button startButton;
    public Button stopButton;
    public Button selectPlan;
    public Button addLocationButton;
    public Button clearLocationsButton;

    private ArrayList currentElements = new ArrayList();

    ListView sensesList;
    ListView drivesList;
    ListView elementsList;

    DrivesListAdapter drivesAdapter;
    ElementsListAdapter elementsAdapter;
    NoElementsListAdapter noElementsAdapter;

    SensesListAdapter sensesListAdapter;

    private int planResourceId;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.OVERLAY);

//        plannerLog = findViewById(R.id.textPlannerLog);
//        currentDriveName = findViewById(R.id.currentDrive);
//        currentElementName = findViewById(R.id.currentElement);
//        checkedSenses = findViewById(R.id.checkedSenses);
//        rootLayout = findViewById(R.id.root_layout);

        // initialize the behavior library
        // HOLLY FUCK: this constructor saves itself into a singleton variable which is used by the planner
        behaviourLibrary = new POSHBehaviourLibrary();
        // behaviourLibrary = new BenediktBehaviourLibrary();

        behaviourLibrary.setPepperLog(this);

        // TODO: this is a hack - gives direct access to the MainActivity (for some visualization)
        behaviourLibrary.setActivity(this);

        // initialize base POSH stuff
        // ACHTUNG: planner gets access to the behavior library through a singleton. Needs to be fixed.
        //planner = new Planner(this, behaviourLibrary);
        planner = new PlannerWithPriorities(this, behaviourLibrary);

        // Pepper server
        pepperServer = new PepperServer(this);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        // TODO: why is this line here and not up there with the initialization of the behavior library?
        // Register the RobotLifecycleCallbacks to this Activity.
        QiSDK.register(this, behaviourLibrary);


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
            /*
            // NOTE: this was experimental. If we remove listeners here, then we have to add them
            //       again in run or start
            new Thread(() -> {
                behaviourLibrary.removeListeners();
            }).start();
            */

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

        sensesList = (ListView) findViewById(R.id.senses_list);
        drivesList = (ListView) findViewById(R.id.drives_list);
        elementsList = (ListView) findViewById(R.id.elements_list);

        // configure for chosen plan
        setSelectedPlan("Plan", R.raw.plan);
    }

    @Override
    protected void onDestroy() {
        // Unregister the RobotLifecycleCallbacks for this Activity.
        QiSDK.unregister(this, behaviourLibrary);
        pepperServer.destroy();
        super.onDestroy();
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
        loadPlanFromFile();
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
            new PlanListItem("Plan Working",           R.raw.plan_working),
            new PlanListItem("Plan Emotion Game",      R.raw.plan_emotion_game),
            new PlanListItem("Plan Test",              R.raw.plan_test),
            new PlanListItem("Uploaded",      -1),
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

    TreeMap<String, String> activatedSenses = new TreeMap<>();
    ArrayList<String> activeSensesList = new ArrayList<>();

    @Override
    public void checkedBooleanSense(String tag, Sense sense, boolean value) {
        String formattedMessage = String.format("%s: %b", sense, value);

        appendLog(tag, "Checked sense - "+formattedMessage, false);
        notifyABOD3(sense.getNameOfElement(), "S");

        runOnUiThread(() -> {
            if (!activatedSenses.containsKey(sense.getNameOfElement())) {
                activeSensesList.add(sense.getNameOfElement());
            }
            activatedSenses.put(sense.getNameOfElement(), "" + value);
        });

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

        runOnUiThread(() -> {
            if (!activatedSenses.containsKey(sense.getNameOfElement())) {
                activeSensesList.add(sense.getNameOfElement());
            }
            activatedSenses.put(sense.getNameOfElement(), "" + value);
        });

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

    public void toggleToolBarView(View view) {
        LinearLayout toolBar = findViewById(R.id.toolBarLayout);
        if(toolBar.getVisibility() == View.INVISIBLE) {
            toolBar.setVisibility(View.VISIBLE);
        } else {
            toolBar.setVisibility(View.INVISIBLE);
        }
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
        runOnUiThread(() -> {
            if (element != null && (element instanceof ActionEvent || element instanceof ActionPatternElement)) {
                currentElements.add(element);
            }
        });
    }

    @Override
    public void clearCurrentElements() {
        currentElements.clear();
        activatedSenses.clear();
        activeSensesList.clear();
    }

    @Override
    public void notifyABOD3(String name, String type) {
        String message = String.format("ABOD3,%s,%s,%d", name, type, planner.getIteration());
//        this.appendLog(TAG, message, false);
        pepperServer.sendMessage(message);
    }

    public void loadPlanFromFile(View view) {
        loadPlanFromFile();
    }

    private void loadPlanFromFile()
    {
        // check if there is a valid file to be read
        InputStream planFile = null;
        if(planResourceId != -1) {
            planFile = getResources().openRawResource(planResourceId);
        } else if(pepperServer.lastUploadedPlan != null) {
            planFile = new ByteArrayInputStream(pepperServer.lastUploadedPlan.getBytes());
        } else {
            Log.d(TAG, "ERROR: NO VALID PLAN ");
            return;
        }

        // HACK: this needs to be somewhere else
        planner.reset();
        // reset the library
        // TODO: we need to create a new instance each time to make sure all runtime things are reset
        //behaviourLibrary.reset();

        // clear display
        drivesAdapter = null;
        sensesListAdapter = null;
        clearCurrentElements();

        Log.d(TAG, "READING PLAN: " + planResourceId);

        // reset the singleton plan
        Plan.getInstance().cleanAllLists();

        // TODO: under the hood this loads the singleton plan planner.getCurrentPlan()
        XPOSHPlanReader planReader = new XPOSHPlanReader();
        planReader.readFile(planFile);

        // initialize the planer with a new plan
        planner.initialize(Plan.getInstance());

        // display the new plan
        displayPlan();
    }

    private void displayPlan()
    {
        Plan plan = Plan.getInstance();

        if (drivesAdapter == null || drivesAdapter.isStale(plan.getCurrentDrive())) {
            drivesAdapter = new DrivesListAdapter(this, plan.getDriveCollections(), plan.getCurrentDrive());
            drivesList.setAdapter(drivesAdapter);
        } else if(drivesAdapter.isStale(plan.getCurrentDrive())) {
            // TODO: this doesn't seem to work for some reason
            drivesAdapter.notifyDataSetChanged();
            Log.d(TAG, "displayPlan: currentDrive" + plan.getCurrentDrive());
        }

        // TODO: create a new adapter to update the values
        if(sensesListAdapter == null || true) {
            sensesListAdapter = new SensesListAdapter(this, activeSensesList, activatedSenses);
            sensesList.setAdapter(sensesListAdapter);
        }
        Log.d(TAG, "sensesListAdapter " + activeSensesList.size() + " - " + sensesListAdapter.getCount());
        for(String key: activatedSenses.keySet()){
            Log.d(TAG, "blub: " + key + "  " + activatedSenses.get(key));
        }
        sensesListAdapter.notifyDataSetChanged();

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

    public void runPlan()
    {
        clearLog();

        stopRunningPlan = false;

        final Handler handler = new Handler();
        Runnable planRunner = new Runnable()
        {
            int iteration = 1;
            boolean completed = false;
            long lastTimeStamp = System.currentTimeMillis();

            @Override
            public void run()
            {
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

                    // debug the execution time
                    long timeStamp = System.currentTimeMillis();
                    appendLog("Plan Step Duration: " + (timeStamp - lastTimeStamp) + " (" + (int)(1000.0 / ((timeStamp - lastTimeStamp))) + " fps)");
                    lastTimeStamp = timeStamp;

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
}
