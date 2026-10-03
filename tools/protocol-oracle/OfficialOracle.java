import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Runs unmodified vendor DEX methods in an isolated Android app_process.
 * No vendor Application is started, and no Bluetooth connection is opened.
 * APKs are supplied locally on CLASSPATH, never bundled with OpenMOBI.
 */
public final class OfficialOracle {
    static final String DEVICE = "com.anytum.mobi.device.";
    // Only the environmental preferences dependency is replaced; computation stays in the APK.
    public static final class OracleApplication extends android.app.Application {
        final Map<String, Object> preferences = new HashMap<>();
        @Override public String getPackageName() { return "org.openmobifitness.oracle"; }
        @Override public android.content.SharedPreferences getSharedPreferences(String name, int mode) {
            final Object[] editor = new Object[1];
            editor[0] = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{android.content.SharedPreferences.Editor.class}, (p, m, a) -> {
                    if (m.getName().startsWith("put")) { preferences.put((String)a[0], a[1]); return editor[0]; }
                    if (m.getName().equals("commit")) return true;
                    if (m.getName().equals("clear")) preferences.clear();
                    if (m.getName().equals("remove")) preferences.remove(a[0]);
                    return m.getReturnType() == void.class ? null : editor[0];
                });
            return (android.content.SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{android.content.SharedPreferences.class}, (p, m, a) -> {
                    if (m.getName().equals("edit")) return editor[0];
                    if (m.getName().equals("getAll")) return new HashMap<>(preferences);
                    if (m.getName().equals("contains")) return preferences.containsKey(a[0]);
                    if (m.getName().startsWith("get")) return preferences.containsKey(a[0]) ? preferences.get(a[0]) : a[1];
                    return null;
                });
        }
    }
    static void bootstrap(String utilClass) throws Exception {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        OracleApplication application = new OracleApplication();
        for (Field f : Class.forName(utilClass).getDeclaredFields()) {
            if (f.getType() == android.app.Application.class && Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true); f.set(null, application);
            }
        }
    }
    static Object instance(String name) throws Exception { return Class.forName(name).getField("INSTANCE").get(null); }
    static Object call(Object target, String name, Object... args) throws Exception {
        Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass();
        for (Method m : type.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            boolean fits = true;
            Class<?>[] params = m.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                Class<?> p = params[i];
                if (p == int.class) p = Integer.class;
                if (p == byte.class) p = Byte.class;
                if (p == boolean.class) p = Boolean.class;
                if (p == double.class) p = Double.class;
                if (args[i] != null && !p.isInstance(args[i])) fits = false;
            }
            if (fits) return m.invoke(target instanceof Class ? null : target, args);
        }
        throw new NoSuchMethodException(type.getName() + "." + name + Arrays.toString(args));
    }
    static JSONObject getters(Object value) throws Exception {
        JSONObject out = new JSONObject();
        for (Method m : value.getClass().getMethods()) {
            if (!m.getName().startsWith("get") || m.getName().equals("getClass") || m.getParameterCount() != 0) continue;
            Class<?> t = m.getReturnType();
            if (t.isPrimitive() || t == String.class) {
                Object v = m.invoke(value);
                if (v instanceof Double && !Double.isFinite((Double)v)) v = v.toString();
                out.put(m.getName().substring(3), v);
            }
        }
        return out;
    }
    static void profile(int kind, int subtype) throws Exception {
        Class<?> type = Class.forName("com.anytum.database.db.entity.MobiDeviceType");
        Object deviceType = type.getConstructor(int.class, byte.class).newInstance(kind, (byte)subtype);
        Object entity = Class.forName("com.anytum.database.db.entity.MobiDeviceEntity").getConstructor().newInstance();
        call(entity, "setDeviceType", deviceType);
        call(entity, "setUninitializedBox", false);
        call(instance(DEVICE + "MobiDeviceInfo"), "setCurrentMobiDeviceEntity", entity);
    }
    static void field(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    static Object privateCall(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true); return method.invoke(target, args);
    }
    static Object jsonNull(Object value) { return value==null ? JSONObject.NULL : value; }
    static JSONObject execute(JSONObject q) throws Exception {
        JSONObject out = new JSONObject().put("input", q);
        String op = q.getString("op");
        if (op.equals("openMobiV1")) {
            // Run the shipped OpenMOBI APK as a second executable reference. This
            // branch has no dependency on the vendor bootstrap or vendor classes.
            Object protocols = instance("org.openmobifitness.core.Protocols");
            JSONArray packets = q.getJSONArray("packets"), values = new JSONArray();
            for (int i=0;i<packets.length();i++) {
                String hex=packets.getString(i); byte[] bytes=new byte[hex.length()/2];
                for(int j=0;j<bytes.length;j++) bytes[j]=(byte)Integer.parseInt(hex.substring(j*2,j*2+2),16);
                Object metrics=call(protocols,"v1Metrics",bytes,true);
                JSONObject row=new JSONObject().put("packet",hex);
                for(String property:new String[]{"Cadence","Resistance","HeartBpm","PowerW"})
                    row.put(property,metrics==null ? JSONObject.NULL : jsonNull(call(metrics,"get"+property)));
                Object range=call(protocols,"v1Range",(Object)bytes);
                row.put("maximum",range==null ? JSONObject.NULL : call(range,"getMax"));
                Object unrounded=call(protocols,"v1Metrics",bytes,false);
                row.put("energyPower",unrounded==null ? JSONObject.NULL : jsonNull(call(unrounded,"getPowerW")));
                JSONArray commands=new JSONArray();
                for(int level=1;level<=24;level++) {
                    byte[] command=(byte[])call(protocols,"v1Resistance",bytes,level);
                    StringBuilder text=new StringBuilder();
                    for(byte b:command) text.append(String.format(Locale.ROOT,"%02x",b&255));
                    commands.put(text.toString());
                }
                values.put(row.put("commands",commands));
            }
            return out.put("values",values);
        }
        if (op.equals("inspect")) {
            Class<?> c = Class.forName(q.getString("class"));
            JSONArray constructors = new JSONArray(), methods = new JSONArray();
            for (Constructor<?> k : c.getDeclaredConstructors()) constructors.put(k.toString());
            for (Method m : c.getDeclaredMethods()) methods.put(m.toString());
            return out.put("constructors", constructors).put("methods", methods);
        }
        if (op.equals("wireHelpers")) {
            Class<?> tool = Class.forName(DEVICE + "tools.ToolsKt");
            Class<?> incline = Class.forName(DEVICE + "tools.WillTreadmillInclineToolKt");
            JSONArray rows = new JSONArray();
            for (int i = 0; i < 256; i++) rows.put(new JSONObject().put("byte", i)
                .put("mode", call(tool, "getReadWriteMode", i)).put("incline", call(tool, "getIncline", (byte)i))
                .put("will", call(incline, "realValue2ShowValue", i-128)));
            return out.put("values", rows);
        }
        if (op.equals("resistanceRamp")) {
            profile(q.getInt("kind"), q.getInt("subtype"));
            call(instance(DEVICE + "ResistanceConstant"), "setResistanceMax$mobidevice_release", q.getInt("maximum"));
            Object motion = instance("com.anytum.mobi.motionData.MotionData");
            field(motion,"mTargetResistance",0);
            Class<?> math = Class.forName("com.anytum.mobi.sportstatemachine.SportStateMachineToolKt");
            JSONArray values = new JSONArray(), events=q.getJSONArray("events");
            for(int i=0;i<events.length();i++) {
                JSONObject event=events.getJSONObject(i);
                if(event.has("resistance")) privateCall(motion,"setResistance",new Class<?>[]{int.class},event.getInt("resistance"));
                if(event.optBoolean("tick")) privateCall(motion,"updateCurveData",new Class<?>[]{});
                int level=(Integer)privateCall(motion,"getAvgResistanceInQueue",new Class<?>[]{});
                values.put(new JSONObject().put("target",level)
                    .put("power",call(math,"calculatePower",q.getDouble("rpm"),level,0))
                    .put("kcalPerSecond",call(math,"calculateCalories",q.getDouble("rpm"),level,q.optInt("weight",70))));
            }
            return out.put("values",values);
        }
        if (op.equals("rowing")) {
            String name = q.optBoolean("model3209") ? "RowingAnalysis3209" : "RowingAnalysis";
            Object model = Class.forName(DEVICE + "data." + name).getConstructor().newInstance();
            call(model, "changeType", q.getInt("type"));
            call(model, "setBoat", q.optInt("boat", 0));
            if (q.optBoolean("reset")) call(model, "reset");
            JSONArray samples = q.getJSONArray("samples"), values = new JSONArray();
            String mode = q.optString("mode", "interval");
            for (int i = 0; i < samples.length(); i++) {
                if (mode.equals("water")) {
                    JSONArray s = samples.getJSONArray(i);
                    call(model, "appendOldWater", s.getInt(0), s.getInt(1));
                } else if (mode.equals("magnet")) call(model, "appendOldMagnet", samples.getInt(i));
                else if (mode.equals("flag")) {
                    JSONArray s = samples.getJSONArray(i);
                    call(model, "append", s.getInt(0), s.getInt(1));
                } else call(model, "append", samples.getInt(i), q.optBoolean("forceNormal", true));
                values.put(getters(model));
            }
            return out.put("values", values);
        }
        if (op.equals("commands")) {
            // Stop the transport consumer, then inspect the unmodified APK's queued writes.
            // Command methods execute normally; no observable is subscribed, no BLE I/O occurs.
            Class<?> qc = Class.forName(DEVICE + "bluetoothLe.bleTool.BleGattMangerQueue");
            Field queueField = qc.getDeclaredField("queue"); queueField.setAccessible(true);
            java.util.concurrent.BlockingQueue<?> queue = (java.util.concurrent.BlockingQueue<?>)queueField.get(null);
            queue.clear();
            Class<?> loopClass = Class.forName("com.anytum.base.util.LoopTask");
            Class<?> loopCallback = Class.forName(loopClass.getName()+"$ILoopCallback");
            Object loop = loopClass.getConstructor(loopCallback).newInstance(Proxy.newProxyInstance(loopCallback.getClassLoader(),new Class<?>[]{loopCallback},(p,m,a)->null));
            call(loop,"setMIsLoop",true);
            Field task = qc.getDeclaredField("task"); task.setAccessible(true); task.set(null,loop);
            Class<?> hc = Class.forName(DEVICE + "bluetoothLe.handler." + q.getString("handler"));
            Object handler = hc.getConstructor().newInstance();
            Field manager = hc.getSuperclass().getDeclaredField("bleManager"); manager.setAccessible(true);
            Constructor<?> managerConstructor = manager.getType().getDeclaredConstructor(); managerConstructor.setAccessible(true);
            manager.set(handler,managerConstructor.newInstance());
            Class<?> bc = Class.forName("com.clj.fastble.data.BleDevice");
            Object device = bc.getConstructor(android.bluetooth.BluetoothDevice.class).newInstance(new Object[]{null});
            call(handler,"setCurrentBleDevice",device);
            int code=q.getInt("command"), value=q.getInt("value");
            if(q.getString("handler").equals("HuanTongHandler")) {
                call(handler,"setHuanTongRes",value);
                for(Method m:hc.getDeclaredMethods()) if(m.getName().contains("huanTongTimedTask") && Modifier.isStatic(m.getModifiers()) && m.getParameterCount()==5) {
                    m.setAccessible(true); m.invoke(null,(byte)0x20,(byte)0xc1,handler,device,0L);
                }
            } else {
                if(code==7) {
                    call(instance(DEVICE+"TreadmillConstant"),"setSupportFold$mobidevice_release",true);
                    call(instance(DEVICE+"TreadmillConstant"),"setFoldedState$mobidevice_release",q.optInt("folded",1));
                }
                if(q.getString("handler").equals("V1Handler")) {
                    JSONArray t=q.optJSONArray("template");
                    if(t!=null) { byte[] bytes=new byte[t.length()]; for(int i=0;i<bytes.length;i++) bytes[i]=(byte)t.getInt(i); field(handler,"ellipticalData",bytes); }
                }
                Class<?> info=Class.forName(DEVICE+"data.DeviceInfo"), control=Class.forName(DEVICE+"data.DeviceControl");
                Object descriptor=info.getConstructor(int.class,int.class,int.class).newInstance(0,0,0);
                Object command=control.getConstructor(int.class,int.class,info).newInstance(code,value,descriptor);
                String method=q.optBoolean("unlock") ? "controlDevice" : q.getString("handler").equals("V2Handler") ? "bleVer0x02Control" : q.getString("handler").equals("FtmsHandler") ? "bleVerFtmsControl" : "controlDevice";
                Method target=hc.getDeclaredMethod(method,control); target.setAccessible(true); target.invoke(handler,command);
            }
            JSONArray writes=new JSONArray(); Object observable;
            while((observable=queue.poll())!=null) {
                Field source=observable.getClass().getDeclaredField("source"); source.setAccessible(true);
                Object subscription=source.get(observable); JSONObject write=new JSONObject();
                for(Field f:subscription.getClass().getDeclaredFields()) {
                    f.setAccessible(true); Object valueField=f.get(subscription);
                    if(valueField instanceof byte[]) { JSONArray bytes=new JSONArray(); for(byte b:(byte[])valueField) bytes.put(b&255); write.put("bytes",bytes); }
                    if(valueField instanceof String) { String s=(String)valueField; if(s.length()==4) write.put("characteristic",s); else if(s.length()==36) write.put("service",s); }
                }
                writes.put(write);
            }
            return out.put("writes",writes);
        }
        if (op.equals("power")) {
            profile(q.getInt("kind"), q.getInt("subtype"));
            call(instance(DEVICE + "ResistanceConstant"), "setResistanceMax$mobidevice_release", q.getInt("maximum"));
            Class<?> math = Class.forName("com.anytum.mobi.sportstatemachine.SportStateMachineToolKt");
            double rpm = q.getDouble("rpm"); int level = q.getInt("level");
            if(q.optInt("model",0)!=0) return out.put("power",call(math,"calculatePower",rpm,level,q.getInt("model")))
                .put("kcalPerHour",call(math,"calculateCalories",rpm,level));
            return out.put("power", call(math, "calculatePower", rpm, level, q.optInt("model", 0)))
                .put("kcalPerSecond", call(math, "calculateCalories", rpm, level, q.optInt("weight", 70)));
        }
        if (op.equals("rowMath")) {
            Class<?> math=Class.forName("com.anytum.mobi.sportstatemachine.SportStateMachineToolKt");
            double power=(Double)call(math,"calculatePowerRow",q.getDouble("speed"),q.getInt("model"));
            return out.put("power",power).put("kcalPerSecond",call(math,"calculateCaloriesRowPerSec",power,q.getInt("model")));
        }
        if (op.equals("packets")) {
            int subtype = q.optInt("subtype", 0);
            profile(q.getInt("kind"), subtype);
            Object entity = call(instance(DEVICE + "MobiDeviceInfo"), "getCurrentMobiDeviceEntity");
            call(entity, "setMachineType", q.optString("machineType", ""));
            call(instance(DEVICE + "MobiDeviceHardwareConstant"), "setIntervalValue$mobidevice_release", q.optInt("intervalMode", 2));
            Object resistance = instance(DEVICE + "ResistanceConstant");
            call(resistance, "setResistanceMode$mobidevice_release", q.optInt("resistanceMode", 3));
            call(resistance, "setResistanceMin$mobidevice_release", q.optInt("minimum", 1));
            call(resistance, "setResistanceMax$mobidevice_release", q.optInt("maximum", 24));
            String handlerName = q.getString("handler");
            call(instance(DEVICE + "TreadmillConstant"), "setSupportFold$mobidevice_release",
                q.optBoolean("supportFold", handlerName.equals("V1Handler") && q.getInt("kind")==3 && subtype==18));
            if(q.optBoolean("metadataStates")) {
                call(instance(DEVICE+"TreadmillConstant"),"setFoldedState$mobidevice_release",1);
                call(instance(DEVICE+"MobiDeviceHardwareConstant"),"setSkippingRopControl$mobidevice_release",false);
                call(instance(DEVICE+"MobiDeviceHardwareConstant"),"setDumbbellControl$mobidevice_release",false);
            }
            Class<?> hc = Class.forName(DEVICE + "bluetoothLe.handler." + handlerName);
            Object handler = hc.getConstructor().newInstance();
            call(handler, "setFirstConnect", false);
            Object rowing = call(handler, "getRowingAnalysis");
            call(rowing, "changeType", subtype);
            if (handlerName.equals("V2Handler")) {
                field(handler, "numberMagnets", q.optInt("magnets", 1));
                field(handler, "deviceSubType", (byte)subtype);
            }
            JSONArray events = new JSONArray();
            if(q.optBoolean("captureBus")) {
                Object bus=instance(DEVICE+"MobiDeviceBus");
                Field channel=bus.getClass().getSuperclass().getDeclaredField("channel");channel.setAccessible(true);
                Object proxy=Proxy.newProxyInstance(channel.getType().getClassLoader(),new Class<?>[]{channel.getType()},(p,m,a)->{
                    if(a!=null && a.length==2 && a[0]!=null && a[0].getClass().getName().startsWith("com.anytum.")) {
                        events.put(new JSONObject().put("method","bus").put("args",new JSONArray().put(
                            getters(a[0]).put("type",a[0].getClass().getSimpleName()))));
                    }
                    return null;
                });
                channel.set(bus,proxy);
            }
            Class<?> callback = Class.forName(DEVICE + "callback.DeviceMotionDataCallBack");
            Object listener = Proxy.newProxyInstance(callback.getClassLoader(), new Class<?>[]{callback}, (p, method, a) -> {
                if (method.getDeclaringClass() == Object.class) return null;
                JSONArray values = new JSONArray();
                if (a != null) for (Object value : a) {
                    values.put(value == null ? JSONObject.NULL : value instanceof Number || value instanceof Boolean || value instanceof String ? value : getters(value));
                }
                events.put(new JSONObject().put("method", method.getName()).put("args", values));
                return null;
            });
            call(handler, "setDeviceMotionDataCallBack", listener);
            String suffix = q.getString("callback");
            Class<?> nc = Class.forName(hc.getName() + "$" + suffix);
            Object consumer = null;
            for (Constructor<?> c : nc.getDeclaredConstructors()) {
                c.setAccessible(true);
                if (c.getParameterCount() == 0) consumer = c.newInstance();
                else if (c.getParameterCount() == 1) consumer = c.newInstance(handler);
                else if (c.getParameterCount() == 2 && c.getParameterTypes()[0] == String.class) consumer = c.newInstance("0000ffe4-0000-1000-8000-00805f9b34fb", handler);
                else if (c.getParameterCount() == 2 && c.getParameterTypes()[0] == hc && c.getParameterTypes()[1].getSimpleName().equals("BleDevice"))
                    consumer = c.newInstance(handler,c.getParameterTypes()[1].getConstructor(android.bluetooth.BluetoothDevice.class).newInstance(new Object[]{null}));
            }
            if (consumer == null) throw new IllegalStateException("unknown callback constructor");
            JSONArray packets = q.getJSONArray("packets"), outputs = new JSONArray(), states = new JSONArray();
            for (int i = 0; i < packets.length(); i++) {
                JSONArray packet = packets.getJSONArray(i); byte[] data = new byte[packet.length()];
                for (int j = 0; j < data.length; j++) data[j] = (byte)packet.getInt(j);
                int begin = events.length();
                call(consumer, "invoke", (Object)data);
                JSONArray emitted = new JSONArray();
                for (int j = begin; j < events.length(); j++) emitted.put(events.get(j));
                outputs.put(emitted);
                if(q.optBoolean("metadataStates")) {
                    JSONObject state=new JSONObject().put("treadmill",getters(instance(DEVICE+"TreadmillConstant")))
                        .put("hardware",getters(instance(DEVICE+"MobiDeviceHardwareConstant")));
                    if(handlerName.equals("V2Handler")) {
                        Field status=hc.getDeclaredField("machineStatus");status.setAccessible(true);
                        state.put("machineStatus",status.get(handler));
                    }
                    if(handlerName.equals("FtmsHandler")) state.put("machineStatus",call(handler,"getMMachineStatus"));
                    states.put(state);
                }
            }
            if(q.optBoolean("metadataStates")) out.put("states",states);
            return out.put("values", outputs).put("resistance", getters(resistance));
        }
        if (op.equals("metadata")) {
            Class<?> enumTools = Class.forName("com.anytum.database.db.EnumDeviceKt");
            JSONArray result = new JSONArray();
            for (int kind = 0; kind < 4; kind++) for (int subtype = 0; subtype < 32; subtype++) {
                result.put(new JSONObject().put("kind", kind).put("subtype", subtype)
                    .put("name", call(enumTools, "getDeviceSubtypeName", kind, subtype)));
            }
            return out.put("values", result);
        }
        if (op.equals("ftms")) {
            Class<?> flags = Class.forName(DEVICE + "bluetoothLe.ftms." + q.getString("class"));
            Object companion = flags.getField("Companion").get(null);
            if (!q.has("packets")) {
                JSONArray layout = new JSONArray();
                for (Object f : flags.getEnumConstants()) layout.put(getters(f).put("name", ((Enum<?>)f).name()));
                return out.put("layout", layout);
            }
            if (q.has("name") && q.getString("class").equals("CrossTrainerDataFlags")) call(companion, "setSDeviceName", q.getString("name"));
            JSONArray packets = q.getJSONArray("packets"), result = new JSONArray();
            for (int i = 0; i < packets.length(); i++) {
                JSONArray input = packets.getJSONArray(i); byte[] data = new byte[input.length()];
                for (int j = 0; j < data.length; j++) data[j] = (byte)input.getInt(j);
                List<?> values = (List<?>)call(companion, "convertBytesToData", (Object)data);
                JSONObject fields = new JSONObject();
                for (Object f : values) fields.put(((Enum<?>)f).name(), call(f, "getValue"));
                result.put(fields);
            }
            return out.put("values", result);
        }
        throw new IllegalArgumentException("unknown op: " + op);
    }
    public static void main(String[] args) throws Exception {
        if (android.os.Looper.getMainLooper() == null) android.os.Looper.prepareMainLooper();
        if(args.length==0 || !args[0].equals("openmobi")) bootstrap(args.length == 0 ? "com.blankj.utilcode.util.q" : args[0]);
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = reader.readLine()) != null) {
            try { System.out.println(execute(new JSONObject(line))); }
            catch (Throwable error) {
                while (error.getCause()!=null && (error instanceof InvocationTargetException || error instanceof ExceptionInInitializerError)) error = error.getCause();
                System.out.println(new JSONObject().put("input", new JSONObject(line)).put("error", error.toString()));
                for (StackTraceElement frame : Arrays.copyOf(error.getStackTrace(), Math.min(12, error.getStackTrace().length))) System.err.println(frame);
            }
        }
        System.exit(0);
    }
}
