import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Same wire sequences, executed against each APK's own code. No copied formula. */
public final class SequenceOracle {
    static final String D = OfficialOracle.DEVICE;
    static final String C = "org.openmobifitness.core.";
    static final Map<Field,Object> defaults = new LinkedHashMap<>();
    static Object call(Object o,String n,Object... a) throws Exception { return OfficialOracle.call(o,n,a); }
    static Object singleton(String n) throws Exception { return OfficialOracle.instance(n); }
    static Object error(Throwable e) throws Exception {
        while(e.getCause()!=null && (e instanceof InvocationTargetException || e instanceof ExceptionInInitializerError)) e=e.getCause();
        JSONArray stack=new JSONArray(); for(int i=0;i<Math.min(5,e.getStackTrace().length);i++) stack.put(e.getStackTrace()[i].toString());
        return new JSONObject().put("type",e.getClass().getName()).put("message",e.getMessage()).put("stack",stack);
    }
    static Object value(Object o,int depth) throws Exception {
        if(o==null) return JSONObject.NULL;
        if(o instanceof Double && !Double.isFinite((Double)o)) return o.toString();
        if(o instanceof Number || o instanceof Boolean || o instanceof String) return o;
        if(o instanceof Enum) return ((Enum<?>)o).name();
        if(depth<=0) return o.getClass().getName();
        JSONObject out=new JSONObject();
        Method[] methods=o.getClass().getMethods(); Arrays.sort(methods,Comparator.comparing(Method::getName));
        for(Method m:methods) {
            if(!m.getName().startsWith("get") || m.getName().equals("getClass") || m.getParameterCount()!=0 || m.getName().contains("$")) continue;
            if(Collection.class.isAssignableFrom(m.getReturnType()) || Map.class.isAssignableFrom(m.getReturnType())) continue;
            if(m.getReturnType().getName().startsWith("android.")) continue;
            out.put(m.getName().substring(3),value(m.invoke(o),depth-1));
        }
        return out;
    }
    static byte[] bytes(JSONArray a) throws Exception { byte[] b=new byte[a.length()]; for(int i=0;i<b.length;i++) b[i]=(byte)a.getInt(i); return b; }
    static Object readField(Object o,String n) throws Exception { Field f=o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o); }
    static void resetConstants() throws Exception {
        if(defaults.isEmpty()) for(String n:new String[]{"ResistanceConstant","TreadmillConstant","InclineConstant","MobiDeviceHardwareConstant","RowingMachineConstant"}) {
            for(Field f:Class.forName(D+n).getDeclaredFields()) if(Modifier.isStatic(f.getModifiers()) && !Modifier.isFinal(f.getModifiers()) && (f.getType().isPrimitive() || f.getType()==String.class)) {
                f.setAccessible(true); defaults.put(f,f.get(null));
            }
        }
        for(Map.Entry<Field,Object> e:defaults.entrySet()) e.getKey().set(null,e.getValue());
    }
    static void bus(String name,JSONArray events) throws Exception {
        Object b=singleton(name); Field f=b.getClass().getSuperclass().getDeclaredField("channel"); f.setAccessible(true);
        f.set(b,Proxy.newProxyInstance(f.getType().getClassLoader(),new Class<?>[]{f.getType()},(p,m,a)->{
            if(a!=null && a.length>0 && a[0]!=null && a[0].getClass().getName().startsWith("com.anytum."))
                events.put(new JSONObject().put("method","bus").put("type",a[0].getClass().getSimpleName()).put("value",value(a[0],2)));
            return null;
        }));
    }
    static String suffix(String protocol,String chr,String mode) {
        if(protocol.equals("V1")) return "bleVer0x01DeviceNotifyProcessing$1";
        if(protocol.equals("HUANTONG")) return "handlerData$1";
        String[] chars=protocol.equals("V2") ? new String[]{"2a19","2a19","2a37","8901","8902","8a01","8801","8802","8803","8804","8805","8806","8807","8808","880e","8811","8812","8812","8813","8814","88e1"}
            : new String[]{"2a19","2a19","2a37","2acc","2acd","2ace","2ad1","2ad2","2ad3","2ad3","2ad4","2ad5","2ad6","2ad7","2ad8","2ada"};
        for(int i=0;i<chars.length;i++) if(chars[i].equals(chr)) {
            if(i+1<chars.length && chars[i+1].equals(chr) && !mode.equals("read")) i++;
            return "handlerData$"+(i+2)+"$1";
        }
        throw new IllegalArgumentException("Unmapped characteristic "+chr);
    }
    static Object consumer(Object handler,String suffix,String characteristic) throws Exception {
        Class<?> hc=handler.getClass(), nc=Class.forName(hc.getName()+"$"+suffix);
        for(Constructor<?> c:nc.getDeclaredConstructors()) {
            c.setAccessible(true); Class<?>[] t=c.getParameterTypes();
            if(t.length==0) return c.newInstance();
            if(t.length==1 && t[0]==hc) return c.newInstance(handler);
            if(t.length==2 && t[0]==String.class) return c.newInstance("0000"+characteristic+"-0000-1000-8000-00805f9b34fb",handler);
            if(t.length==2 && t[0]==hc && t[1].getSimpleName().equals("BleDevice"))
                return c.newInstance(handler,t[1].getConstructor(android.bluetooth.BluetoothDevice.class).newInstance(new Object[]{null}));
        }
        throw new IllegalArgumentException("Unmapped constructor "+nc);
    }
    static JSONObject vendorState(Object handler) throws Exception {
        JSONObject s=new JSONObject();
        for(String n:new String[]{"ResistanceConstant","TreadmillConstant","InclineConstant","MobiDeviceHardwareConstant"}) s.put(n,value(singleton(D+n),2));
        Object entity=call(singleton(D+"MobiDeviceInfo"),"getCurrentMobiDeviceEntity");
        s.put("battery",call(entity,"getBattery")).put("deviceType",value(call(entity,"getDeviceType"),2)).put("hardware",call(entity,"getMachineType"));
        if(handler.getClass().getSimpleName().equals("V2Handler")) s.put("magnets",readField(handler,"numberMagnets")).put("machineStatus",readField(handler,"machineStatus"));
        if(handler.getClass().getSimpleName().equals("FtmsHandler")) s.put("machineStatus",call(handler,"getMMachineStatus"));
        return s;
    }
    static JSONObject vendor(JSONObject q) throws Exception {
        resetConstants(); int kind=q.getInt("kind"),sub=q.getInt("subtype");
        OfficialOracle.profile(kind==16?7:kind-9,sub);
        Object entity=call(singleton(D+"MobiDeviceInfo"),"getCurrentMobiDeviceEntity"); call(entity,"setName",q.getString("name"));
        String protocol=q.getString("protocol");
        String handlerName=protocol.equals("FTMS")?"FtmsHandler":protocol.equals("HUANTONG")?"HuanTongHandler":protocol+"Handler";
        Object h=Class.forName(D+"bluetoothLe.handler."+handlerName).getConstructor().newInstance();
        call(h,"setFirstConnect",true);
        JSONArray trace=new JSONArray(),events=new JSONArray(); bus(D+"MobiDeviceBus",events); bus("com.anytum.mobi.sportstatemachineInterface.SportStateMachineBus",events);
        Object subscription=null;
        if(q.optBoolean("captureRx")) {
            Class<?> consumer=Class.forName("io.reactivex.functions.Consumer");
            Object sink=Proxy.newProxyInstance(consumer.getClassLoader(),new Class<?>[]{consumer},(p,m,a)->{
                if(m.getName().equals("accept")) events.put(new JSONObject().put("method","rxBus").put("type",a[0].getClass().getSimpleName()).put("value",value(a[0],2)));
                return null;
            });
            subscription=call(call(singleton("com.anytum.base.event.RxBus"),"toObservable"),"subscribe",sink);
        }
        Object motion=null,forward=null;
        if(q.optBoolean("motion")) {
            motion=singleton("com.anytum.mobi.motionData.MotionData");
            call(motion,"clearData$sportstatemachine_release");
            OfficialOracle.privateCall(motion,"setResistance",new Class<?>[]{int.class},1);
            Object machine=singleton("com.anytum.mobi.motionData.MotionStateMachine");
            // This initialization API clears restartApp, avoiding settlement of the
            // preceding synthetic workout when the next case changes equipment type.
            call(machine,"setBleType",kind==16?7:kind-9);
            Class<?> sport=Class.forName("com.anytum.mobi.sportstatemachineInterface.SportState");
            // Pulse calculations start active. Treadmill handlers initially report
            // stopped; seeding START would create an artificial START -> STOP
            // settlement and demand the unavailable Application/upload context.
            // Do not alter packet states or bypass the original numeric callbacks.
            Object delegate=readField(machine,"sportStatus$delegate"); boolean set=false;
            for(Class<?> t=delegate.getClass();t!=null;t=t.getSuperclass()) for(Field f:t.getDeclaredFields()) {
                f.setAccessible(true);
                if(sport.isInstance(f.get(delegate))) { f.set(delegate,Enum.valueOf((Class)sport,q.optString("initialSportState","START"))); set=true; }
            }
            if(!set) throw new IllegalStateException("Cannot isolate official workout timer");
            Class<?> f=Class.forName("com.anytum.mobi.motionData.MotionData$initMobiDeviceListener$1");
            Constructor<?> ctor=f.getDeclaredConstructor(); ctor.setAccessible(true); forward=ctor.newInstance();
        }
        final Object downstream=forward;
        Class<?> cb=Class.forName(D+"callback.DeviceMotionDataCallBack");
        Object listener=Proxy.newProxyInstance(cb.getClassLoader(),new Class<?>[]{cb},(p,m,a)->{
            if(m.getDeclaringClass()==Object.class) return m.getName().equals("toString")?"SequenceCallback":null;
            JSONArray args=new JSONArray(); if(a!=null) for(Object v:a) args.put(value(v,2));
            JSONObject event=new JSONObject().put("method",m.getName()).put("args",args); events.put(event);
            if(downstream!=null) try { m.invoke(downstream,a); } catch(Throwable e) { event.put("downstreamError",error(e)); }
            return null;
        });
        call(h,"setDeviceMotionDataCallBack",listener);
        JSONArray steps=q.getJSONArray("steps");
        for(int i=0;i<steps.length();i++) {
            JSONObject step=steps.getJSONObject(i),row=new JSONObject().put("index",i); int start=events.length();
            try {
                if(step.has("tick")) {
                    if(motion==null) throw new IllegalArgumentException("tick requires motion=true");
                    OfficialOracle.privateCall(motion,"updateCurveData",new Class<?>[]{});
                } else {
                    String chr=step.getString("chr");
                    call(consumer(h,suffix(protocol,chr,step.optString("mode","notify")),chr),"invoke",(Object)bytes(step.getJSONArray("data")));
                }
            } catch(Throwable e) { row.put("error",error(e)); }
            JSONArray emitted=new JSONArray(); for(int j=start;j<events.length();j++) emitted.put(events.get(j));
            row.put("events",emitted).put("state",vendorState(h));
            if(motion!=null) row.put("motion",value(motion,2));
            trace.put(row);
        }
        if(subscription!=null) call(subscription,"dispose");
        return new JSONObject().put("id",q.getString("id")).put("trace",trace);
    }
    static JSONObject open(JSONObject q) throws Exception {
        String protocol=q.getString("protocol"),name=q.getString("name");
        Object p=Enum.valueOf((Class)Class.forName(C+"Protocol"),protocol);
        Object session=protocol.equals("FTMS")?Class.forName(C+"MobiFtmsSession").getConstructor(String.class).newInstance(name):Class.forName(C+"MobiSession").getConstructor(p.getClass(),String.class).newInstance(p,name);
        Object merged=Class.forName(C+"Metrics").getConstructor().newInstance();
        JSONArray steps=q.getJSONArray("steps"),trace=new JSONArray();
        for(int i=0;i<steps.length();i++) {
            JSONObject step=steps.getJSONObject(i),row=new JSONObject().put("index",i); Object metrics=null;
            try {
                if(step.has("tick")) call(session,"activeSecond");
                else {
                    String chr=step.getString("chr"); byte[] b=bytes(step.getJSONArray("data"));
                    if(chr.equals("2a37")) row.put("heart",value(call(singleton(C+"Protocols"),"heart",(Object)b),1));
                    else if(protocol.equals("FTMS")) {
                        if(chr.equals("2ad6")) call(session,"setRange",call(singleton(C+"Protocols"),"mobiFtmsRange",(Object)b));
                        else { call(session,"metadata",chr,b); if(Arrays.asList("2acd","2ace","2ad1","2ad2").contains(chr)) metrics=call(session,"receive",chr,b); }
                    } else if(chr.equals("2a19")) call(session,"battery",(Object)b);
                    else metrics=call(session,"receive",chr,b);
                }
                if(metrics!=null) merged=call(merged,"merge",metrics);
            } catch(Throwable e) { row.put("error",error(e)); }
            row.put("metrics",value(metrics,2)).put("merged",value(merged,2)).put("state",value(session,3)); trace.put(row);
        }
        return new JSONObject().put("id",q.getString("id")).put("trace",trace);
    }
    public static void main(String[] args) throws Exception {
        if(android.os.Looper.getMainLooper()==null) android.os.Looper.prepareMainLooper();
        boolean open=args[0].equals("openmobi");
        if(!open) OfficialOracle.bootstrap(args[0].equals("chinese")?"com.blankj.utilcode.util.z":"com.blankj.utilcode.util.q");
        BufferedReader in=new BufferedReader(new InputStreamReader(System.in)); String line;
        while((line=in.readLine())!=null) {
            JSONObject q=new JSONObject(line),out;
            try { out=open?open(q):vendor(q); } catch(Throwable e) { out=new JSONObject().put("id",q.optString("id")).put("error",error(e)); }
            System.out.println(out);
        }
        System.exit(0);
    }
}
