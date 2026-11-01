package com.brian.wiiuremote;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.*;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.util.SparseArray;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

public final class MainActivity extends Activity {
  private final ControllerClient client = new ControllerClient();
  private PadView pad;
  private String host;
  private boolean resumed, pro, classic, motionPlus;
  private int remoteOrientation;
  private final PhoneMotion motion = new PhoneMotion();
  private final GyroPointer aim = new GyroPointer();
  private boolean gyroAim;
  private float aimSensitivity = 1, aimSmoothing = .06f;
  private int player = 3;
  private ConnectivityManager connectivity;
  private ConnectivityManager.NetworkCallback wifiChanges;
  private final ControllerClient.SocketBinder wifiBinder = socket -> {
    if (connectivity == null)
      throw new java.net.SocketException("Wi-Fi unavailable");
    for (Network network : connectivity.getAllNetworks()) {
      NetworkCapabilities caps = connectivity.getNetworkCapabilities(network);
      if (caps != null &&
          (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
           caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
        network.bindSocket(socket);
        return;
      }
    }
    throw new java.net.SocketException(
        "Connect phone to the Wii U local Wi-Fi");
  };
  private float sensitivity = 1f, deadzone = .08f, opacity = 1f;
  private boolean invertRightY = false, haptics = true, showLabels = true,
                  showStats = false, snapGrid = true;
  private static final int EXPORT_LAYOUT = 71, IMPORT_LAYOUT = 72;
  private void readSettings() {
    gyroAim = getPreferences(0).getBoolean("gyroAim", true) && (sensors == null || gyroscope != null);
    aimSensitivity = Math.max(.25f, Math.min(3, getPreferences(0).getFloat("aimSensitivity", 1)));
    aimSmoothing = Math.max(0, Math.min(.2f, getPreferences(0).getFloat("aimSmoothing", .06f)));
    sensitivity = getPreferences(0).getFloat("sensitivity", 1f);
    deadzone = getPreferences(0).getFloat("deadzone", .08f);
    opacity = getPreferences(0).getFloat("opacity", 1f);
    invertRightY = getPreferences(0).getBoolean("invertRightY", false);
    haptics = getPreferences(0).getBoolean("haptics", true);
    showLabels = getPreferences(0).getBoolean("showLabels", true);
    showStats = getPreferences(0).getBoolean("showStats", false);
    snapGrid = getPreferences(0).getBoolean("snapGrid", true);
  }
  private SensorManager sensors;
  private Sensor accelerometer, gyroscope;
  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                         WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    connectivity = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
    client.setSocketBinder(wifiBinder);
    wifiChanges = new ConnectivityManager.NetworkCallback() {
      @Override
      public void onAvailable(Network network) {
        client.networkChanged();
      }
      @Override
      public void onLost(Network network) {
        client.networkChanged();
      }
    };
    connectivity.registerNetworkCallback(
        new NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build(),
        wifiChanges);
    readSettings();
    pro = getPreferences(0).getBoolean("pro", true);
    classic = getPreferences(0).getBoolean("classic", false);
    player = Math.max(1, Math.min(4, getPreferences(0).getInt("player", 3)));
    sensors = (SensorManager)getSystemService(SENSOR_SERVICE);
    accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    gyroscope = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    gyroAim &= gyroscope != null;
    motionPlus = getPreferences(0).getBoolean("motionPlus", false) && gyroscope != null;
    remoteOrientation = getPreferences(0).getInt("remoteOrientation", 0);
    applyOrientation();
    pad = new PadView();
    setContentView(pad);
    pad.post(() -> connectionSettings());
  }
  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    if (host != null)
      client.start(host);
    registerMotion();
  }
  @Override
  protected void onPause() {
    resumed = false;
    pad.clear();
    client.stop();
    sensors.unregisterListener(pad);
    super.onPause();
  }
  @Override
  protected void onDestroy() {
    client.stop();
    if (connectivity != null && wifiChanges != null)
      connectivity.unregisterNetworkCallback(wifiChanges);
    super.onDestroy();
  }
  @Override
  public void onWindowFocusChanged(boolean focused) {
    super.onWindowFocusChanged(focused);
    if (!focused && pad != null)
      pad.clear();
  }
  private void applyOrientation() {
    int orientation = pro ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        : remoteOrientation == 1 ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        : remoteOrientation == 2 ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
    setRequestedOrientation(orientation);
  }
  @Override public void onConfigurationChanged(android.content.res.Configuration config) {
    super.onConfigurationChanged(config);
    if (pad != null) { pad.clear(); pad.builtW = -1; }
    resetMotion();
  }
  private void registerMotion() {
    sensors.unregisterListener(pad);
    if (!resumed || pro) return;
    if ((pad.tilt || motionPlus) && accelerometer != null)
      sensors.registerListener(pad, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    if ((motionPlus || gyroAim) && gyroscope != null)
      sensors.registerListener(pad, gyroscope, SensorManager.SENSOR_DELAY_GAME);
  }
  private void connectionSettings() {
    pad.clear();
    client.stop();
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(36, 12, 36, 12);
    EditText address = new EditText(this);
    address.setSingleLine(true);
    address.setInputType(InputType.TYPE_CLASS_TEXT |
                         InputType.TYPE_TEXT_VARIATION_URI);
    address.setText(host != null
                        ? host
                        : getPreferences(0).getString("host", "192.168.1.100"));
    address.setHint("Wii U IPv4 address");
    content.addView(address);
    Button find = new Button(this);
    find.setText("Find Wii U on Wi-Fi");
    content.addView(find);
    find.setOnClickListener(v -> {
      find.setEnabled(false);
      find.setText("Searching...");
      new Thread(() -> {
        try {
          String ip = ControllerDiscovery.find(wifiBinder);
          runOnUiThread(() -> {
            address.setText(ip);
            find.setText("Found " + ip);
            find.setEnabled(true);
          });
        } catch (Exception e) {
          runOnUiThread(() -> {
            find.setEnabled(true);
            find.setText("Find Wii U on Wi-Fi");
            Toast
                .makeText(
                    this,
                    "No Wii U found. Check Aroma and Wi-Fi, or enter its IP.",
                    Toast.LENGTH_LONG)
                .show();
          });
        }
      }, "WiiU-discovery").start();
    });
    Spinner playerPicker = new Spinner(this);
    playerPicker.setAdapter(new ArrayAdapter<String>(
        this, android.R.layout.simple_spinner_dropdown_item,
        new String[] {"Player 1", "Player 2", "Player 3", "Player 4"}));
    playerPicker.setSelection(player - 1);
    content.addView(playerPicker);
    Spinner mode = new Spinner(this);
    mode.setAdapter(new ArrayAdapter<String>(
        this, android.R.layout.simple_spinner_dropdown_item,
        new String[] {"Wii Remote", "Pro Controller", "Classic Controller", "Wii Remote + MotionPlus"}));
    mode.setSelection(classic ? 2 : pro ? 1 : motionPlus ? 3 : 0);
    content.addView(mode);
    TextView note = new TextView(this);
    note.setText("Use the same local network as the Wii U. Install the "
                 + "updated Aroma plugin first.\n\nTouch MODE to switch later. "
                 + "Some games require you to join again after switching.");
    note.setPadding(0, 20, 0, 12);
    content.addView(note);
    ScrollView scroller = new ScrollView(this);
    scroller.addView(content);
    AlertDialog dialog = new AlertDialog.Builder(this)
                             .setTitle("Connect Wii U controller")
                             .setView(scroller)
                             .setPositiveButton("Connect", null)
                             .setNegativeButton("Cancel",
                                                (d, w) -> {
                                                  if (resumed && host != null)
                                                    client.start(host);
                                                })
                             .create();
    dialog.setOnShowListener(
        v
        -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
               .setOnClickListener(button -> {
                 String value = address.getText().toString().trim();
                 if (!validAddress(value)) {
                   address.setError(
                       "Enter an IPv4 address, for example 192.168.1.100");
                   return;
                 }
                 host = value;
                 if (mode.getSelectedItemPosition() == 3 && gyroscope == null) {
                   Toast.makeText(this, "MotionPlus requires a real gyroscope", Toast.LENGTH_LONG).show(); return;
                 }
                 classic = mode.getSelectedItemPosition() == 2;
                 motionPlus = mode.getSelectedItemPosition() == 3;
                 pro = mode.getSelectedItemPosition() == 1 || classic;
                 resetMotion(); applyOrientation(); registerMotion();
                 player = playerPicker.getSelectedItemPosition() + 1;
                 getPreferences(0)
                     .edit()
                     .putString("host", host)
                     .putBoolean("pro", pro)
                     .putBoolean("classic", classic)
                     .putBoolean("motionPlus", motionPlus)
                     .putInt("player", player)
                     .apply();
                 pad.clear();
                 if (resumed)
                   client.start(host);
                 dialog.dismiss();
               }));
    dialog.setOnCancelListener(v -> {
      if (resumed && host != null)
        client.start(host);
    });
    dialog.show();
  }

  private void settings() {
    pad.clear();
    new AlertDialog.Builder(this)
        .setTitle("Controller settings")
        .setItems(
            new String[] {"Connection & player", "Edit button layout",
                          "Stick & touch settings", "Appearance",
                          "Export layouts", "Import layouts",
                          "Reset this mode's layout", "Motion & orientation"},
            (d, which) -> {
              switch (which) {
              case 0:
                connectionSettings();
                break;
              case 1:
                beginEdit();
                break;
              case 2:
                inputSettings();
                break;
              case 3:
                appearanceSettings();
                break;
              case 4:
                startActivityForResult(
                    new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType("application/json")
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_TITLE,
                                  "wiiu-controller-layout.json"),
                    EXPORT_LAYOUT);
                break;
              case 5:
                startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("*/*")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    IMPORT_LAYOUT);
                break;
              case 7:
                motionSettings();
                break;
              case 6:
                new AlertDialog.Builder(this)
                    .setTitle("Reset layout?")
                    .setMessage("Restore the default positions for " +
                                modeName() +
                                " only?")
                    .setPositiveButton(
                        "Reset",
                        (a, b) -> {
                          getPreferences(0).edit().remove(layoutKey()).apply();
                          pad.builtW = -1;
                          pad.invalidate();
                        })
                    .setNegativeButton("Cancel", null)
                    .show();
                break;
              }
            })
        .setNegativeButton("Close", null)
        .show();
  }
  private String modeName() { return classic ? "Classic Controller" : pro ? "Pro Controller" : motionPlus ? "Wii Remote + MotionPlus" : "Wii Remote"; }
  private String layoutKey() { return classic ? "layout.classic" : pro ? "layout.pro" : pad != null && pad.portrait() ? "layout.remotePortrait" : "layout.remote"; }
  private ControllerPacket.State state() {
    ControllerPacket.State s = new ControllerPacket.State(pro, player);
    s.classic = classic;
    s.motionPlus = motionPlus;
    return s;
  }
  private void beginEdit() {
    client.stop();
    pad.clear();
    setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
    pad.editing = true;
    pad.selected = null;
    pad.builtW = -1;
    pad.invalidate();
  }
  private void finishEdit(boolean save) {
    if (save) {
      JSONObject data = new JSONObject();
      try {
        for (PadView.Control c : pad.controls)
          if (pad.editable(c)) {
            JSONArray rect = new JSONArray();
            rect.put(c.rect.left)
                .put(c.rect.top)
                .put(c.rect.width())
                .put(c.rect.height());
            data.put(c.id, rect);
          }
      } catch (JSONException e) {
        Toast.makeText(this, "Could not save layout", Toast.LENGTH_SHORT)
            .show();
        return;
      }
      getPreferences(0).edit().putString(layoutKey(), data.toString()).apply();
    }
    pad.editing = false;
    applyOrientation();
    pad.selected = null;
    pad.builtW = -1;
    pad.clear();
    if (resumed && host != null)
      client.start(host);
  }
  private LinearLayout options() {
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(32, 8, 32, 16);
    return body;
  }
  private SeekBar slider(LinearLayout body, String title, int progress,
                         int maximum) {
    TextView label = new TextView(this);
    label.setText(title + ": " + progress);
    body.addView(label);
    SeekBar bar = new SeekBar(this);
    bar.setMax(maximum);
    bar.setProgress(progress);
    body.addView(bar);
    bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      public void onStartTrackingTouch(SeekBar b) {}
      public void onStopTrackingTouch(SeekBar b) {}
      public void onProgressChanged(SeekBar b, int value, boolean user) {
        label.setText(title + ": " + value);
      }
    });
    return bar;
  }
  private CheckBox check(LinearLayout body, String title, boolean value) {
    CheckBox box = new CheckBox(this);
    box.setText(title);
    box.setChecked(value);
    body.addView(box);
    return box;
  }
  private void inputSettings() {
    LinearLayout body = options();
    SeekBar gain = slider(body, "Stick sensitivity (%)",
                          Math.round(sensitivity * 100), 200);
    SeekBar zone =
        slider(body, "Stick deadzone (%)", Math.round(deadzone * 100), 30);
    CheckBox invert = check(body, "Invert right stick Y", invertRightY);
    CheckBox feedback = check(body, "Button haptic feedback", haptics);
    CheckBox snap = check(body, "Snap layout editor to grid", snapGrid);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(body);
    new AlertDialog.Builder(this)
        .setTitle("Stick & touch")
        .setView(scroll)
        .setPositiveButton(
            "Save",
            (d, w) -> {
              getPreferences(0)
                  .edit()
                  .putFloat("sensitivity",
                            Math.max(.25f, gain.getProgress() / 100f))
                  .putFloat("deadzone", zone.getProgress() / 100f)
                  .putBoolean("invertRightY", invert.isChecked())
                  .putBoolean("haptics", feedback.isChecked())
                  .putBoolean("snapGrid", snap.isChecked())
                  .apply();
              readSettings();
              pad.clear();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }
  private void resetMotion() { motion.reset(); aim.reset(); }
  private void recenterAim() {
    aim.recenter(motion.quaternion);
    if (pad != null) { pad.aimValid = false; pad.invalidate(); }
  }
  private void toggleAim() {
    if (gyroscope == null) {
      Toast.makeText(this, "Gyro aiming requires a hardware gyroscope", Toast.LENGTH_LONG).show(); return;
    }
    gyroAim = !gyroAim;
    getPreferences(0).edit().putBoolean("gyroAim", gyroAim).apply();
    recenterAim(); pad.clear(); pad.builtW = -1; registerMotion();
  }
  private void motionSettings() {
    LinearLayout body = options();
    TextView info = new TextView(this);
    info.setText(gyroscope == null ? "No hardware gyroscope: gyro aiming and MotionPlus unavailable." : "Point the TOP EDGE of your phone at the TV center, then tap CENTER. Touching the pointer pad overrides gyro aim. Recenter if it drifts.");
    body.addView(info);
    CheckBox aiming = check(body, "Gyroscope pointer (Remote modes)", gyroAim);
    aiming.setEnabled(gyroscope != null);
    SeekBar gain = slider(body, "Aim sensitivity (%)", Math.round(aimSensitivity * 100), 300);
    SeekBar smooth = slider(body, "Aim smoothing (ms)", Math.round(aimSmoothing * 1000), 200);
    Spinner orientation = new Spinner(this);
    orientation.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new String[] {"Automatic rotation", "Vertical Wii Remote", "Horizontal Wii Remote"}));
    orientation.setSelection(remoteOrientation); body.addView(orientation);
    Button center = new Button(this); center.setText("Recenter pointer"); body.addView(center);
    center.setOnClickListener(v -> { recenterAim(); Toast.makeText(this, "Pointer recentered", Toast.LENGTH_SHORT).show(); });
    Button motionCenter = new Button(this); motionCenter.setText("Reset MotionPlus orientation"); body.addView(motionCenter);
    motionCenter.setOnClickListener(v -> { resetMotion(); pad.clear(); });
    ScrollView scroll = new ScrollView(this); scroll.addView(body);
    new AlertDialog.Builder(this).setTitle("Motion & pointing").setView(scroll).setPositiveButton("Save", (d,w) -> {
      remoteOrientation = orientation.getSelectedItemPosition();
      gyroAim = aiming.isChecked() && gyroscope != null;
      aimSensitivity = Math.max(.25f, gain.getProgress() / 100f);
      aimSmoothing = smooth.getProgress() / 1000f;
      getPreferences(0).edit().putInt("remoteOrientation", remoteOrientation).putBoolean("gyroAim", gyroAim).putFloat("aimSensitivity", aimSensitivity).putFloat("aimSmoothing", aimSmoothing).apply();
      pad.clear(); resetMotion(); applyOrientation(); registerMotion();
    }).setNegativeButton("Cancel", null).show();
  }
  private void appearanceSettings() {
    LinearLayout body = options();
    SeekBar alpha =
        slider(body, "Control opacity (%)", Math.round(opacity * 100), 100);
    CheckBox labels = check(body, "Show control labels", showLabels),
             stats = check(body, "Show packet diagnostics", showStats);
    TextView colorLabel = new TextView(this);
    colorLabel.setText("Accent color");
    body.addView(colorLabel);
    Spinner color = new Spinner(this);
    color.setAdapter(new ArrayAdapter<String>(
        this, android.R.layout.simple_spinner_dropdown_item,
        new String[] {"Mint", "Blue", "Amber"}));
    color.setSelection(getPreferences(0).getInt("accent", 0));
    body.addView(color);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(body);
    new AlertDialog.Builder(this)
        .setTitle("Appearance")
        .setView(scroll)
        .setPositiveButton(
            "Save",
            (d, w) -> {
              getPreferences(0)
                  .edit()
                  .putFloat("opacity",
                            Math.max(.25f, alpha.getProgress() / 100f))
                  .putBoolean("showLabels", labels.isChecked())
                  .putBoolean("showStats", stats.isChecked())
                  .putInt("accent", color.getSelectedItemPosition())
                  .apply();
              readSettings();
              pad.invalidate();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }
  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null || data.getData() == null)
      return;
    try {
      Uri uri = data.getData();
      if (request == EXPORT_LAYOUT) {
        JSONObject out = new JSONObject();
        out.put("version", 1);
        out.put("classic", new JSONObject(getPreferences(0).getString("layout.classic", "{}")));
        out.put("remotePortrait", new JSONObject(getPreferences(0).getString("layout.remotePortrait", "{}")));
        out.put("pro", new JSONObject(
                           getPreferences(0).getString("layout.pro", "{}")));
        out.put("remote", new JSONObject(getPreferences(0).getString(
                              "layout.remote", "{}")));
        try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
          if (stream == null)
            throw new IOException();
          stream.write(out.toString(2).getBytes("UTF-8"));
        }
        Toast.makeText(this, "Layouts exported", Toast.LENGTH_SHORT).show();
      } else if (request == IMPORT_LAYOUT) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
          if (stream == null)
            throw new IOException();
          byte[] part = new byte[4096];
          int n;
          while ((n = stream.read(part)) != -1) {
            bytes.write(part, 0, n);
            if (bytes.size() > 65536)
              throw new IOException("Layout file is too large");
          }
        }
        JSONObject in = new JSONObject(bytes.toString("UTF-8"));
        if (in.getInt("version") != 1)
          throw new IOException("Unsupported layout version");
        JSONObject a = in.getJSONObject("pro"), b = in.getJSONObject("remote");
        JSONObject c = in.optJSONObject("classic");
        if (c == null) c = new JSONObject();
        JSONObject portrait = in.optJSONObject("remotePortrait");
        if (portrait == null) portrait = new JSONObject();
        validateLayout(portrait);
        validateLayout(c);
        validateLayout(a);
        validateLayout(b);
        getPreferences(0)
            .edit()
            .putString("layout.remotePortrait", portrait.toString())
            .putString("layout.classic", c.toString())
            .putString("layout.pro", a.toString())
            .putString("layout.remote", b.toString())
            .apply();
        pad.clear();
        pad.builtW = -1;
        Toast.makeText(this, "Layouts imported", Toast.LENGTH_SHORT).show();
      }
    } catch (Exception e) {
      Toast
          .makeText(this, "Could not read/write layout: " + e.getMessage(),
                    Toast.LENGTH_LONG)
          .show();
    }
  }
  private void validateLayout(JSONObject layout) throws Exception {
    Iterator<String> names = layout.keys();
    int count = 0;
    while (names.hasNext()) {
      if (++count > 64)
        throw new IOException("Too many controls");
      JSONArray rect = layout.getJSONArray(names.next());
      if (rect.length() != 4)
        throw new IOException("Invalid rectangle");
      LayoutGeometry.fit((float)rect.getDouble(0), (float)rect.getDouble(1),
                         (float)rect.getDouble(2), (float)rect.getDouble(3),
                         false, false);
    }
  }
  @Override
  public void onBackPressed() {
    if (pad.editing)
      finishEdit(false);
    else
      settings();
  }
  private boolean validAddress(String value) {
    String[] parts = value.split("\\.", -1);
    if (parts.length != 4)
      return false;
    try {
      for (String p : parts)
        if (!p.matches("[0-9]{1,3}") || Integer.parseInt(p) > 255)
          return false;
    } catch (NumberFormatException e) {
      return false;
    }
    return true;
  }
  private void toggleMode() {
    int next = ((classic ? 2 : pro ? 1 : motionPlus ? 3 : 0) + 1) % 4;
    if (next == 3 && gyroscope == null) next = 0;
    classic = next == 2;
    pro = next == 1 || next == 2;
    motionPlus = next == 3;
    resetMotion(); applyOrientation(); registerMotion();
    pad.clear();
    getPreferences(0).edit().putBoolean("pro", pro)
                     .putBoolean("classic", classic)
                     .putBoolean("motionPlus", motionPlus).apply();
  }

  private final class PadView extends View implements SensorEventListener {
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final ArrayList<Control> controls = new ArrayList<>();
    final SparseArray<Finger> fingers = new SparseArray<>();
    final int bg = Color.rgb(14, 22, 36), panel = Color.rgb(29, 43, 63),
              ink = Color.rgb(237, 244, 255), muted = Color.rgb(145, 166, 191),
              baseAccent = Color.rgb(90, 221, 197);
    int accent = baseAccent;
    boolean editing = false;
    Control selected;
    int editPointer = -1;
    boolean resizing;
    float dragX, dragY;
    RectF original;
    boolean tilt = false;
    float ax, ay, az = 1000;
    long lastAcceleration;
    boolean aimValid;
    int aimX, aimY;
    int lastRotation = -1;
    float scale = 1, offsetX, offsetY;
    int builtW = -1;
    boolean builtPro, builtClassic;
    PadView() {
      super(MainActivity.this);
      setFocusable(true);
      setContentDescription("Wii U controller");
    }
    final class Control {
      RectF rect;
      String label, id;
      int bit, action;
      boolean circle;
      Control(String label, float x, float y, float w, float h, int bit,
              int action, boolean circle) {
        this.rect = new RectF(x, y, x + w, y + h);
        this.label = label;
        this.id = bit != 0 ? "button:" + bit : "action:" + action;
        this.bit = bit;
        this.action = action;
        this.circle = circle;
      }
    }
    final class Finger {
      Control c;
      float x, y, startX, startY;
      Finger(Control c, float x, float y) {
        this.c = c;
        this.x = this.startX = x;
        this.y = this.startY = y;
      }
    }
    void add(String label, float x, float y, float w, float h, int bit,
             int action) {
      controls.add(new Control(label, x, y, w, h, bit, action, false));
    }
    void round(String label, float x, float y, float size, int bit) {
      controls.add(new Control(label, x, y, size, size, bit, 0, true));
    }
    boolean portrait() { return !pro && getHeight() > getWidth(); }
    float canvasW() { return portrait() ? 460 : 960; }
    float canvasH() { return portrait() ? 960 : 460; }
    int rotation() { return getWindowManager().getDefaultDisplay().getRotation(); }
    int[] remoteDpad() {
      switch (rotation()) {
        case 1: return new int[] {2,8,1,4};
        case 2: return new int[] {4,2,8,1};
        case 3: return new int[] {1,4,2,8};
        default: return new int[] {8,1,4,2};
      }
    }
    void layoutControls() {
      controls.clear();
      if (portrait()) {
        if (editing) { add("RESET", 12, 12, 130, 40, 0, 22); add("CANCEL", 158, 12, 130, 40, 0, 21); add("SAVE", 304, 12, 144, 40, 0, 20); }
        else { add("MODE", 230, 12, 100, 40, 0, 10); add("SETUP", 340, 12, 108, 40, 0, 11); }
      } else if (editing) {
        add("RESET", 500, 12, 130, 40, 0, 22);
        add("CANCEL", 650, 12, 122, 40, 0, 21);
        add("SAVE", 784, 12, 140, 40, 0, 20);
      } else {
        add("MODE", 650, 12, 122, 40, 0, 10);
        add("SETUP", 784, 12, 140, 40, 0, 11);
      }
      if (pro) {
        add("ZL", 20, 68, 145, 46, 0x80, 0);
        add("L", 177, 68, 145, 46, 0x2000, 0);
        add("R", 638, 68, 145, 46, 0x200, 0);
        add("ZR", 795, 68, 145, 46, 4, 0);
        if (!classic) add("L3", 380, 384, 86, 40, 0x20000, 0);
        if (!classic) add("R3", 482, 384, 86, 40, 0x10000, 0);
        add("LEFT STICK", 24, 188, 222, 222, 0, 1);
        add("RIGHT STICK", 704, 188, 222, 222, 0, 2);
        dpad(286, 202, 1, 2, 0x4000, 0x8000);
        round("X", 573, 180, 60, 8);
        round("Y", 517, 244, 60, 32);
        round("A", 629, 244, 60, 16);
        round("B", 573, 308, 60, 64);
        add("-", 380, 122, 65, 44, 0x1000, 0);
        add("HOME", 454, 122, 84, 44, 0x800, 0);
        add("+", 547, 122, 65, 44, 0x400, 0);
      } else if (portrait()) {
        int[] d = remoteDpad(); dpad(142, 140, d[0], d[1], d[2], d[3]);
        round("A", 65, 340, 140, 0x800); round("B", 255, 340, 140, 0x400);
        add("POINTER", 40, 540, 380, 190, 0, 3);
        add(gyroAim ? "AIM ON" : "AIM OFF", 40, 490, 180, 40, 0, 13);
        add("CENTER", 240, 490, 180, 40, 0, 14);
        add("-", 70, 750, 80, 50, 0x1000, 0); add("HOME", 170, 750, 120, 50, 0x8000, 0); add("+", 310, 750, 80, 50, 0x10, 0);
        round("1", 85, 825, 80, 0x200); round("2", 285, 825, 80, 0x100);
        add("SHAKE", 12, 125, 110, 50, 0, 4); add(tilt ? "TILT ON" : "TILT OFF", 338, 125, 110, 50, 0, 12);
      } else {
        int[] d = remoteDpad(); dpad(40, 204, d[0], d[1], d[2], d[3]);
        add("POINTER", 286, 188, 346, 216, 0, 3);
        add(gyroAim ? "AIM ON" : "AIM OFF", 286, 142, 160, 40, 0, 13);
        add("CENTER", 458, 142, 174, 40, 0, 14);
        round("A", 722, 178, 86, 0x800);
        round("B", 822, 244, 76, 0x400);
        round("1", 690, 322, 66, 0x200);
        round("2", 772, 344, 66, 0x100);
        add("-", 284, 90, 70, 46, 0x1000, 0);
        add("HOME", 366, 90, 100, 46, 0x8000, 0);
        add("+", 478, 90, 70, 46, 0x10, 0);
        add("SHAKE", 648, 90, 140, 46, 0, 4);
        add(accelerometer == null ? "NO SENSOR"
            : tilt                ? "TILT ON"
                                  : "TILT OFF",
            800, 90, 140, 46, 0, 12);
      }
      try {
        JSONObject saved =
            new JSONObject(getPreferences(0).getString(layoutKey(), "{}"));
        for (Control c : controls)
          if (editable(c) && saved.has(c.id)) {
            JSONArray r = saved.getJSONArray(c.id);
            float[] fit =
                LayoutGeometry.fit((float)r.getDouble(0), (float)r.getDouble(1),
                                   (float)r.getDouble(2), (float)r.getDouble(3),
                                   c.circle, c.action >= 1 && c.action <= 3, canvasW(), canvasH());
            c.rect.set(fit[0], fit[1], fit[0] + fit[2], fit[1] + fit[3]);
          }
      } catch (Exception ignored) {
      }
      builtW = getWidth();
      builtPro = pro;
      builtClassic = classic;
    }
    void dpad(float x, float y, int up, int left, int down, int right) {
      add("UP", x + 60, y, 56, 56, up, 0);
      add("LEFT", x, y + 60, 56, 56, left, 0);
      add("DOWN", x + 60, y + 120, 56, 56, down, 0);
      add("RIGHT", x + 120, y + 60, 56, 56, right, 0);
      if (!pro) for (int i = controls.size() - 4; i < controls.size(); i++) controls.get(i).id = "dpad:" + controls.get(i).label;
    }
    void clear() {
      fingers.clear();
      aimValid = false;
      client.publish(state());
      invalidate();
    }
    boolean held(Control c) {
      for (int i = 0; i < fingers.size(); i++)
        if (fingers.valueAt(i).c == c)
          return true;
      return false;
    }
    void label(Canvas c, String s, float x, float y, int color, float size) {
      paint.setColor(color);
      paint.setTextSize(size);
      paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      c.drawText(s, x, y, paint);
    }
    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      canvas.drawColor(bg);
      scale = Math.min(getWidth() / canvasW(), getHeight() / canvasH());
      offsetX = (getWidth() - canvasW() * scale) / 2;
      offsetY = (getHeight() - canvasH() * scale) / 2;
      if (lastRotation != rotation()) { lastRotation = rotation(); clear(); resetMotion(); builtW = -1; }
      if (builtW != getWidth() || builtPro != pro || builtClassic != classic)
        layoutControls();
      publish();
      canvas.save();
      canvas.translate(offsetX, offsetY);
      canvas.scale(scale, scale);
      int palette = getPreferences(0).getInt("accent", 0);
      accent = palette == 1   ? Color.rgb(105, 178, 255)
               : palette == 2 ? Color.rgb(255, 195, 98)
                              : baseAccent;
      if (!portrait() || !editing) label(canvas, editing ? "EDIT LAYOUT" : "PLAYER " + player, 24, 34,
            accent, 22);
      label(canvas, modeName().toUpperCase(java.util.Locale.ROOT), portrait() ? 16 : 162, portrait() ? 78 : 34, ink, portrait() ? 18 : 20);
      boolean connected = client.lastAck > 0 &&
                          SystemClock.elapsedRealtime() - client.lastAck < 1000;
      label(
          canvas,
          editing
              ? "Drag a control; drag its corner to resize. SAVE keeps changes."
          : host == null ? "Tap SETUP to choose player and connect"
                         : (connected ? "CONNECTED  " : "WAITING  ") + host,
          24, portrait() ? 108 : 61, connected ? accent : muted, 14);
      for (Control b : controls) {
        int layer = canvas.saveLayerAlpha(
            b.rect, editing ? 255 : Math.round(opacity * 255));
        boolean pressed = held(b);
        paint.setColor(pressed ? Color.rgb(38, 94, 96) : panel);
        if (b.circle)
          canvas.drawOval(b.rect, paint);
        else
          canvas.drawRoundRect(b.rect, 14, 14, paint);
        if (b.action == 1 || b.action == 2) {
          float cx = b.rect.centerX(), cy = b.rect.centerY(), dx = 0, dy = 0;
          for (int i = 0; i < fingers.size(); i++) {
            Finger f = fingers.valueAt(i);
            if (f.c == b) {
              dx = Math.max(-70, Math.min(70, f.x - f.startX));
              dy = Math.max(-70, Math.min(70, f.y - f.startY));
            }
          }
          paint.setColor(Color.rgb(44, 65, 90));
          float radius = Math.min(b.rect.width(), b.rect.height()) * .34f;
          canvas.drawCircle(cx, cy, radius, paint);
          paint.setColor(accent);
          canvas.drawCircle(cx + dx / 70 * radius * .6f,
                            cy + dy / 70 * radius * .6f, radius * .32f, paint);
          paint.setTextSize(14);
          if (showLabels || editing)
            label(canvas, b.label, cx - paint.measureText(b.label) / 2,
                  b.rect.bottom - 14, muted, 14);
        } else if (b.action == 3) {
          label(canvas, "POINTER", b.rect.left + 18, b.rect.top + 30, ink, 18);
          label(canvas, gyroAim ? "Gyro aim / touch override" : "Touch to aim", b.rect.left + 18, b.rect.bottom - 18,
                muted, 15);
          if (aimValid && !held(b)) {
            float px = b.rect.left + (aimX / 156f + 1) * .5f * b.rect.width();
            float py = b.rect.top + (aimY / 156f + 1) * .5f * b.rect.height();
            paint.setColor(accent);
            canvas.drawCircle(px, py, 8, paint);
            canvas.drawLine(px - 18, py, px + 18, py, paint);
            canvas.drawLine(px, py - 18, px, py + 18, paint);
          }
          for (int i = 0; i < fingers.size(); i++) {
            Finger f = fingers.valueAt(i);
            if (f.c == b) {
              paint.setColor(accent);
              canvas.drawCircle(f.x, f.y, 10, paint);
              canvas.drawLine(f.x - 22, f.y, f.x + 22, f.y, paint);
              canvas.drawLine(f.x, f.y - 22, f.x, f.y + 22, paint);
            }
          }
        } else if (showLabels || editing || !editable(b)) {
          paint.setTextSize(b.bit != 0 && b.label.length() < 3 ? 26 : 16);
          float size = paint.getTextSize();
          label(canvas, b.label,
                b.rect.centerX() - paint.measureText(b.label) / 2,
                b.rect.centerY() + size * .35f, ink, size);
        }
        canvas.restoreToCount(layer);
        if (editing && b == selected) {
          paint.setColor(accent);
          paint.setStyle(Paint.Style.STROKE);
          paint.setStrokeWidth(2);
          canvas.drawRoundRect(b.rect, 12, 12, paint);
          paint.setStyle(Paint.Style.FILL);
          canvas.drawRect(b.rect.right - 18, b.rect.bottom - 18, b.rect.right,
                          b.rect.bottom, paint);
        }
      }
      String footer =
          editing ? (selected == null
                         ? "Choose any control. Toolbar is fixed so it stays "
                               + "reachable."
                         : selected.label + "  |  " +
                               Math.round(selected.rect.width()) + " x " +
                               Math.round(selected.rect.height()))
          : !client.error.isEmpty() ? client.error
          : !showStats
              ? "Multi-touch ready  |  SETUP for layouts, sticks and appearance"
              : ("Sent " + client.sent + "   Replies " + client.replies +
                 "   |   Same Wi-Fi as Wii U");
      int maxChars = portrait() ? 49 : 106;
      if (footer.length() > maxChars) footer = footer.substring(0, maxChars);
      label(canvas, footer, 24, canvasH() - 20, muted, 14);
      canvas.restore();
      postInvalidateDelayed(33);
    }
    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
      if (builtW < 0)
        layoutControls();
      int action = event.getActionMasked(), index = event.getActionIndex(),
          id = event.getPointerId(index);
      float x = (event.getX(index) - offsetX) / scale,
            y = (event.getY(index) - offsetY) / scale;
      if (editing)
        return editTouch(event, x, y);
      if (action == android.view.MotionEvent.ACTION_DOWN)
        fingers.clear();
      for (int i = 0; i < event.getPointerCount(); i++) {
        Finger existing = fingers.get(event.getPointerId(i));
        if (existing != null) {
          existing.x = (event.getX(i) - offsetX) / scale;
          existing.y = (event.getY(i) - offsetY) / scale;
        }
      }
      if (action == android.view.MotionEvent.ACTION_DOWN ||
          action == android.view.MotionEvent.ACTION_POINTER_DOWN) {
        for (Control b : controls)
          if (b.rect.contains(x, y)) {
            if (b.action == 10) {
              toggleMode();
              return true;
            }
            if (b.action == 11) {
              settings();
              return true;
            }
            if (b.action == 13) { toggleAim(); return true; }
            if (b.action == 14) {
              recenterAim(); Toast.makeText(MainActivity.this, "Pointer centered — aim the phone's top edge", Toast.LENGTH_SHORT).show();
              return true;
            }
            if (b.action == 12) {
              if (accelerometer == null) {
                Toast
                    .makeText(MainActivity.this,
                              "No accelerometer on this device",
                              Toast.LENGTH_SHORT)
                    .show();
                return true;
              }
              tilt = !tilt;
              clear();
              builtW = -1;
              registerMotion();
              return true;
            }
            if ((b.action == 1 || b.action == 2 || b.action == 3) && held(b))
              break;
            fingers.put(id, new Finger(b, x, y));
            if (haptics && b.bit != 0)
              performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            break;
          }
      } else if (action == android.view.MotionEvent.ACTION_MOVE) {
        for (int i = 0; i < event.getPointerCount(); i++) {
          Finger f = fingers.get(event.getPointerId(i));
          if (f != null) {
            f.x = (event.getX(i) - offsetX) / scale;
            f.y = (event.getY(i) - offsetY) / scale;
          }
        }
      } else if (action == android.view.MotionEvent.ACTION_UP ||
                 action == android.view.MotionEvent.ACTION_POINTER_UP)
        fingers.remove(id);
      else if (action == android.view.MotionEvent.ACTION_CANCEL)
        fingers.clear();
      publish();
      invalidate();
      return true;
    }
    boolean editable(Control c) {
      return c.action != 10 && c.action != 11 && c.action < 20;
    }
    boolean editTouch(MotionEvent e, float x, float y) {
      int action = e.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN) {
        selected = null;
        editPointer = e.getPointerId(0);
        for (Control c : controls)
          if (!editable(c) && c.rect.contains(x, y)) {
            if (c.action == 20)
              finishEdit(true);
            else if (c.action == 21)
              finishEdit(false);
            else if (c.action == 22) {
              String saved = getPreferences(0).getString(layoutKey(), null);
              getPreferences(0).edit().remove(layoutKey()).apply();
              layoutControls();
              if (saved != null)
                getPreferences(0).edit().putString(layoutKey(), saved).apply();
            }
            return true;
          }
        for (int i = controls.size() - 1; i >= 0; i--) {
          Control c = controls.get(i);
          if (editable(c) && c.rect.contains(x, y)) {
            selected = c;
            original = new RectF(c.rect);
            dragX = x;
            dragY = y;
            resizing = x > c.rect.right - 28 && y > c.rect.bottom - 28;
            break;
          }
        }
      } else if (action == MotionEvent.ACTION_MOVE && selected != null) {
        int i = e.findPointerIndex(editPointer);
        if (i < 0)
          return true;
        float dx = (e.getX(i) - offsetX) / scale - dragX,
              dy = (e.getY(i) - offsetY) / scale - dragY;
        if (snapGrid) {
          dx = Math.round(dx / 8) * 8;
          dy = Math.round(dy / 8) * 8;
        }
        float[] f = LayoutGeometry.fit(
            original.left + (resizing ? 0 : dx),
            original.top + (resizing ? 0 : dy),
            original.width() + (resizing ? dx : 0),
            original.height() + (resizing ? dy : 0), selected.circle,
            selected.action >= 1 && selected.action <= 3, canvasW(), canvasH());
        selected.rect.set(f[0], f[1], f[0] + f[2], f[1] + f[3]);
      } else if (action == MotionEvent.ACTION_UP ||
                 action == MotionEvent.ACTION_CANCEL)
        editPointer = -1;
      invalidate();
      return true;
    }
    int stickAxis(float delta, float radius) {
      float value = Math.abs(delta) / radius;
      if (value < deadzone)
        return 0;
      return Math.round(Math.copySign(
          Math.min(1f, (value - deadzone) / (1f - deadzone) * sensitivity) *
              156,
          delta));
    }
    void publish() {
      ControllerPacket.State state = state();
      if (editing) {
        client.publish(state);
        return;
      }
      if ((tilt || motionPlus) && !pro && SystemClock.elapsedRealtime() - lastAcceleration < 200) {
        state.ax = (int)ax;
        state.ay = (int)ay;
        state.az = (int)az;
      }
      if (motionPlus && !pro) {
        state.motionValid = motion.fresh(SystemClock.elapsedRealtime() / 1000.0);
        state.gyro = motion.rate.clone(); state.quaternion = motion.quaternion.clone();
      }
      aimValid = false;
      double now = SystemClock.elapsedRealtime() / 1000.0;
      if (!pro && gyroAim && motion.fresh(now) && aim.update(motion.quaternion, now, aimSensitivity, aimSmoothing)) {
        aimValid = true; aimX = Math.round(aim.x()*156); aimY = Math.round(aim.y()*156);
        state.pointer = true; state.px = aimX; state.py = aimY;
      } else aim.invalidate();
      for (int i = 0; i < fingers.size(); i++) {
        Finger f = fingers.valueAt(i);
        Control b = f.c;
        if (b.action == 1 || b.action == 2) {
          float radius = Math.min(b.rect.width(), b.rect.height()) * .315f;
          int sx = stickAxis(f.x - f.startX, radius),
              sy = stickAxis(f.startY - f.y, radius);
          if (b.action == 1) {
            state.lx = sx;
            state.ly = sy;
          } else {
            state.rx = sx;
            state.ry = invertRightY ? -sy : sy;
          }
        } else if (b.action == 3) {
          state.pointer = true;
          state.px =
              Math.round(((f.x - b.rect.left) / b.rect.width() * 2 - 1) * 156);
          state.py =
              Math.round(((f.y - b.rect.top) / b.rect.height() * 2 - 1) * 156);
        } else if (b.action == 4) {
          state.ax = SystemClock.elapsedRealtime() / 70 % 2 == 0 ? 3000 : -3000;
          state.az = 1000;
        } else if (b.rect.contains(f.x, f.y))
          state.buttons |= b.bit;
      }
      client.publish(state);
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
      if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
        motion.update(event.timestamp / 1e9, event.values[0], event.values[1], event.values[2], rotation());
      } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
        lastAcceleration = SystemClock.elapsedRealtime();
        float[] a = PhoneMotion.rotate(event.values[0], event.values[1], event.values[2], rotation());
        ax = a[0] / SensorManager.GRAVITY_EARTH * 1000;
        ay = a[1] / SensorManager.GRAVITY_EARTH * 1000;
        az = a[2] / SensorManager.GRAVITY_EARTH * 1000;
      }
      if ((tilt || motionPlus || gyroAim) && !pro) publish();
    }
    @Override
    public void onAccuracyChanged(Sensor s, int accuracy) {}
  }
}
