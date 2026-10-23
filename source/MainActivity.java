package uk.michael.calmhome;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.util.LruCache;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.concurrent.*;

/** Native, offline TV home. No services, ads, analytics, overlays or accessibility hooks. */
public class MainActivity extends Activity {
    static final String[] DEFAULTS={"uk.co.bbc.sounds","com.amazon.firetv.youtube","com.spotify.tv.android","com.bendingx.iptvx.android"};
    static final int COLUMNS=6;
    final Handler inputHandler=new Handler(Looper.getMainLooper());boolean selectHeld=false,heldLong=false,moving=false,browsing=false;String heldPackage="",movingPackage="";ArrayList<String> moveOriginal;android.animation.ObjectAnimator moveAnimation;long lastMove=0;View heroSpace;LinearLayout dockContainer,gridContainer;
    final Runnable holdAction=()->{View focus=getCurrentFocus();if(this.active&&selectHeld&&!moving&&focus!=null&&heldPackage.equals(focus.getTag())&&find(heldPackage)!=null){heldLong=true;appOptions(heldPackage);}};
    final ArrayList<App> apps=new ArrayList<>();
    final ArrayList<App> hiddenApps=new ArrayList<>();
    final ArrayList<View> top=new ArrayList<>(), dock=new ArrayList<>(), grid=new ArrayList<>();
    final LruCache<String,Bitmap> images=new LruCache<String,Bitmap>(12288){protected int sizeOf(String k,Bitmap b){return b.getByteCount()/1024;}};
    final ExecutorService feedWorker=Executors.newSingleThreadExecutor();final ExecutorService worker=Executors.newSingleThreadExecutor();final ExecutorService heroWorker=Executors.newSingleThreadExecutor();final Set<String> heroAttempted=new HashSet<>();final LruCache<String,Bitmap> heroImages=new LruCache<String,Bitmap>(18000){protected int sizeOf(String k,Bitmap b){return b.getByteCount()/1024;}};
    android.content.SharedPreferences prefs;
    HorizontalScrollView favouriteScroll;LinearLayout favouriteRow;String favouriteKey="";final ArrayList<View> favouriteTiles=new ArrayList<>();final Set<String> favouriteLoading=new HashSet<>();final Map<String,Long> favouriteAttempts=new HashMap<>();ArrayList<ShelfRepository.Entry> stationFavourites=new ArrayList<>();
    View appScroll,appHeading,footer; LinearLayout root; Shelf shelf; TextView clock,date,greeting; Button network; Typeface font;
    String lastFocus=""; boolean dirty=true,registered=false;volatile boolean active=false; int generation=0,navigationVersion=0;
    ConnectivityManager connectivity;boolean frameObserverRegistered=false;
    final android.database.ContentObserver frameObserver=new android.database.ContentObserver(new Handler(Looper.getMainLooper())){public void onChange(boolean self){if(active)refreshShelf();}};
    final BroadcastReceiver timeReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){updateClock();}};
    final BroadcastReceiver packagesReceiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){dirty=true;}};
    final ConnectivityManager.NetworkCallback networkCallback=new ConnectivityManager.NetworkCallback(){
        public void onAvailable(Network n){runOnUiThread(()->updateNetwork());}
        public void onLost(Network n){runOnUiThread(()->updateNetwork());}
        public void onCapabilitiesChanged(Network n,NetworkCapabilities c){runOnUiThread(()->updateNetwork());}
        public void onLinkPropertiesChanged(Network n,LinkProperties p){runOnUiThread(()->updateNetwork());}
    };
    static class App {String pkg,label; Intent launch; ResolveInfo info; App(String p,String l,Intent i,ResolveInfo r){pkg=p;label=l;launch=i;info=r;}}
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        prefs=getSharedPreferences("home",MODE_PRIVATE);
        if(!prefs.getBoolean("iptvxDockMigration",false)){prefs.edit().putString("dock3","com.bendingx.iptvx.android").putBoolean("iptvxDockMigration",true).apply();}
        if(!prefs.getBoolean("frameDockMigration",false)&&getPackageManager().getLaunchIntentForPackage("uk.michael.framefire")!=null){prefs.edit().putString("dock3","uk.michael.framefire").putBoolean("frameDockMigration",true).apply();}
        lastFocus=state!=null?state.getString("focus",""):prefs.getString("focus","");
        try{font=Typeface.createFromAsset(getAssets(),"Inter.ttf");}catch(Exception e){font=Typeface.SANS_SERIF;}
        connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        IntentFilter tf=new IntentFilter(Intent.ACTION_TIME_TICK);tf.addAction(Intent.ACTION_TIME_CHANGED);tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);registerReceiver(timeReceiver,tf);
        IntentFilter pf=new IntentFilter();pf.addAction(Intent.ACTION_PACKAGE_ADDED);pf.addAction(Intent.ACTION_PACKAGE_REMOVED);pf.addAction(Intent.ACTION_PACKAGE_CHANGED);pf.addDataScheme("package");registerReceiver(packagesReceiver,pf);
        
        try{getContentResolver().registerContentObserver(Uri.parse("content://uk.michael.framefire.shelf/recent"),true,frameObserver);frameObserverRegistered=true;}catch(Exception ignored){}
    }
    public void onResume(){super.onResume();active=true;if(dirty){discover();render();dirty=false;}updateClock();updateNetwork();if(shelf!=null)shelf.showItem();refreshShelf();}
    public void onPause(){inputHandler.removeCallbacksAndMessages(null);selectHeld=false;heldLong=false;active=false;navigationVersion++;if(moving){applyOrder(moveOriginal);moving=false;movingPackage="";if(moveAnimation!=null)moveAnimation.cancel();dirty=true;}if(shelf!=null)shelf.stop();super.onPause();prefs.edit().putString("focus",lastFocus).apply();}
    public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putString("focus",lastFocus);}
    public void onDestroy(){if(frameObserverRegistered)getContentResolver().unregisterContentObserver(frameObserver);unregisterReceiver(timeReceiver);unregisterReceiver(packagesReceiver);if(registered)connectivity.unregisterNetworkCallback(networkCallback);inputHandler.removeCallbacksAndMessages(null);if(shelf!=null)shelf.stop();feedWorker.shutdownNow();worker.shutdownNow();heroWorker.shutdownNow();super.onDestroy();}
    public void onBackPressed(){if(moving){finishMove(true);return;}if(browsing){setBrowsing(false);if(!dock.isEmpty())dock.get(0).requestFocus();return;}if(!dock.isEmpty())dock.get(Math.min(COLUMNS-1,Math.max(0,shelf==null?0:shelf.ownerSlot()))).requestFocus();else if(!top.isEmpty())top.get(0).requestFocus();}
    void discover(){
        apps.clear();hiddenApps.clear();PackageManager pm=getPackageManager();Set<String> seen=new HashSet<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            Intent q=new Intent(Intent.ACTION_MAIN).addCategory(category);
            for(ResolveInfo r:pm.queryIntentActivities(q,0)){
                String p=r.activityInfo.packageName;
                if(p.equals(getPackageName())||seen.contains(p)||!r.activityInfo.exported||!r.activityInfo.enabled||!r.activityInfo.applicationInfo.enabled)continue;seen.add(p);
                Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(category).setComponent(new ComponentName(p,r.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                apps.add(new App(p,r.loadLabel(pm).toString(),launch,r));
            }
        }
        for(ApplicationInfo application:pm.getInstalledApplications(0)){String pkg=application.packageName;if(pkg.equals(getPackageName())||seen.contains(pkg)||!application.enabled)continue;Intent launch=pm.getLeanbackLaunchIntentForPackage(pkg);if(launch==null)launch=pm.getLaunchIntentForPackage(pkg);if(launch==null)continue;ResolveInfo resolved=pm.resolveActivity(launch,0);if(resolved==null||resolved.activityInfo==null||!resolved.activityInfo.exported||!resolved.activityInfo.enabled)continue;seen.add(pkg);launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);apps.add(new App(pkg,application.loadLabel(pm).toString(),launch,resolved));}
        apps.removeIf(a->a.pkg.equals("com.amazon.tv.settings.v2"));Collections.sort(apps,(a,b)->a.label.compareToIgnoreCase(b.label));apps.add(new App("com.amazon.tv.settings.v2","Settings",new Intent(Settings.ACTION_SETTINGS),null));apps.add(new App("calm.stock.home","Fire OS",new Intent(),null));
        ArrayList<String> order=new ArrayList<>();try{org.json.JSONArray saved=new org.json.JSONArray(prefs.getString("appOrder","[]"));for(int i=0;i<saved.length();i++)order.add(saved.getString(i));}catch(Exception ignored){}
        if(order.isEmpty()){for(int i=0;i<4;i++){String pkg=prefs.getString("dock"+i,DEFAULTS[i]);if(!order.contains(pkg))order.add(pkg);}for(String pkg:new String[]{"com.bendingx.iptvx.android","bbc.iplayer.android"})if(!order.contains(pkg))order.add(pkg);}
        applyOrder(order);if(!prefs.getBoolean("referenceDockComplete",false)){App player=find("uk.co.bbc.iplayer");if(player!=null&&apps.size()>5&&apps.get(5).label.contains("Alexa")){apps.remove(player);apps.add(5,player);}prefs.edit().putBoolean("referenceDockComplete",true).apply();}saveOrder();
        hiddenApps.clear();Set<String> hidden=prefs.getStringSet("hiddenApps",Collections.emptySet());for(App a:new ArrayList<>(apps))if(hidden.contains(a.pkg)){hiddenApps.add(a);apps.remove(a);}
    }
    void applyOrder(List<String> order){HashMap<String,Integer> positions=new HashMap<>();for(int i=0;i<order.size();i++)positions.put(order.get(i),i);Collections.sort(apps,(a,b)->Integer.compare(positions.containsKey(a.pkg)?positions.get(a.pkg):100000,positions.containsKey(b.pkg)?positions.get(b.pkg):100000));}
    ArrayList<String> currentOrder(){ArrayList<String> out=new ArrayList<>();for(App app:apps)out.add(app.pkg);return out;}
    void saveOrder(){ArrayList<String> visible=currentOrder(),saved=new ArrayList<>();Set<String> hidden=new HashSet<>();for(App a:hiddenApps)hidden.add(a.pkg);int next=0;try{org.json.JSONArray old=new org.json.JSONArray(prefs.getString("appOrder","[]"));for(int i=0;i<old.length();i++){String pkg=old.getString(i);if(hidden.contains(pkg)){if(!saved.contains(pkg))saved.add(pkg);}else if(next<visible.size()){String pkgNext=visible.get(next++);if(!saved.contains(pkgNext))saved.add(pkgNext);}}}catch(Exception ignored){}while(next<visible.size()){String pkg=visible.get(next++);if(!saved.contains(pkg))saved.add(pkg);}for(App a:hiddenApps)if(!saved.contains(a.pkg))saved.add(a.pkg);prefs.edit().putString("appOrder",new org.json.JSONArray(saved).toString()).apply();}
    String[] favourites(){String[] f=new String[COLUMNS];for(int i=0;i<COLUMNS;i++)f[i]=i<apps.size()?apps.get(i).pkg:"";return f;}
    void beginMove(String pkg){if(moving)return;moving=true;movingPackage=pkg;moveOriginal=currentOrder();lastFocus=pkg;browsing=true;render();Toast.makeText(this,"Move with arrows · Select to place · Back to cancel",Toast.LENGTH_SHORT).show();}
    void finishMove(boolean cancel){if(!moving)return;if(cancel)applyOrder(moveOriginal);else saveOrder();moving=false;lastFocus=movingPackage;movingPackage="";if(moveAnimation!=null)moveAnimation.cancel();render();}
    void moveApp(int key){int from=-1;for(int i=0;i<apps.size();i++)if(apps.get(i).pkg.equals(movingPackage))from=i;if(from<0)return;int to=from;if(key==21&&from%COLUMNS>0)to--;if(key==22&&from%COLUMNS<COLUMNS-1&&from+1<apps.size())to++;if(key==19&&from>=COLUMNS)to-=COLUMNS;if(key==20&&from+COLUMNS<apps.size())to+=COLUMNS;else if(key==20&&from/COLUMNS<(apps.size()-1)/COLUMNS)to=apps.size()-1;if(to==from)return;App picked=apps.remove(from);apps.add(to,picked);lastFocus=movingPackage;reflowTiles();}
    public boolean dispatchKeyEvent(KeyEvent event){int key=event.getKeyCode();boolean select=key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER;View focused=getCurrentFocus();App app=focused==null?null:find(String.valueOf(focused.getTag()));
        if(key==KeyEvent.KEYCODE_MENU&&!moving){if(event.getAction()==KeyEvent.ACTION_UP)configure();return true;}if(moving&&(key==19||key==20||key==21||key==22)){if(event.getAction()==KeyEvent.ACTION_DOWN&&(event.getRepeatCount()==0||android.os.SystemClock.uptimeMillis()-lastMove>180)){lastMove=android.os.SystemClock.uptimeMillis();moveApp(key);}return true;}
        if(moving&&key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)finishMove(true);return true;}
        if(select&&(app!=null||selectHeld||moving)){if(event.getAction()==KeyEvent.ACTION_DOWN){if(event.getRepeatCount()==0){selectHeld=true;heldLong=false;heldPackage=app==null?movingPackage:app.pkg;if(!moving)inputHandler.postDelayed(holdAction,650);}return true;}if(event.getAction()==KeyEvent.ACTION_UP){inputHandler.removeCallbacks(holdAction);boolean wasHeld=selectHeld;selectHeld=false;if(event.isCanceled())return true;if(heldLong){heldLong=false;return true;}if(moving){finishMove(false);return true;}if(wasHeld&&focused!=null)focused.performClick();return true;}}return super.dispatchKeyEvent(event);}
    LinearLayout.LayoutParams spacerParams(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,1,1);lp.setMargins(dp(7),dp(5),dp(7),dp(5));return lp;}
    void reflowTiles(){HashMap<String,android.graphics.Point> positions=new HashMap<>();HashMap<String,View> views=new HashMap<>();ArrayList<View> old=new ArrayList<>();old.addAll(dock);old.addAll(grid);for(View tile:old){int[] xy=new int[2];tile.getLocationOnScreen(xy);positions.put(String.valueOf(tile.getTag()),new android.graphics.Point(xy[0],xy[1]));views.put(String.valueOf(tile.getTag()),tile);tile.animate().cancel();tile.setTranslationX(0);tile.setTranslationY(0);((android.view.ViewGroup)tile.getParent()).removeView(tile);}dockContainer.removeAllViews();gridContainer.removeAllViews();dock.clear();grid.clear();LinearLayout row=null;for(int i=0;i<apps.size();i++){View tile=views.get(apps.get(i).pkg);if(i<COLUMNS){dock.add(tile);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(94),1);lp.setMargins(dp(7),dp(5),dp(7),dp(5));dockContainer.addView(tile,lp);}else{int n=i-COLUMNS;if(n%COLUMNS==0){row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setClipChildren(false);row.setClipToPadding(false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(104));rp.bottomMargin=dp(12);gridContainer.addView(row,rp);}grid.add(tile);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(7),dp(5),dp(7),dp(5));row.addView(tile,lp);}}int remainder=grid.size()%COLUMNS;if(row!=null&&remainder!=0)for(int i=remainder;i<COLUMNS;i++)row.addView(new View(this),spacerParams());wireNavigation();View movingView=views.get(movingPackage);if(movingView!=null)movingView.requestFocus();final int layoutGeneration=generation;final android.view.ViewTreeObserver observer=root.getViewTreeObserver();observer.addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(observer.isAlive())observer.removeOnPreDrawListener(this);if(layoutGeneration!=generation||!active)return true;for(Map.Entry<String,View> entry:views.entrySet()){View tile=entry.getValue();int[] xy=new int[2];tile.getLocationOnScreen(xy);android.graphics.Point was=positions.get(entry.getKey());tile.setTranslationX(was.x-xy[0]);tile.setTranslationY(was.y-xy[1]);tile.animate().translationX(0).translationY(0).setDuration(200).setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();}return true;}});}
    void setBrowsing(boolean show){if(moving)show=true;boolean changed=appScroll!=null&&(appScroll.getVisibility()==View.VISIBLE)!=show;int[] old=new int[2];if(dockContainer!=null)dockContainer.getLocationOnScreen(old);browsing=show;if(heroSpace!=null)heroSpace.setVisibility(show?View.GONE:View.VISIBLE);if(appScroll!=null)appScroll.setVisibility(show?View.VISIBLE:View.GONE);if(appHeading!=null)appHeading.setVisibility(show?View.VISIBLE:View.GONE);if(shelf!=null){shelf.animate().cancel();shelf.setAlpha(show?0f:1f);shelf.setVisibility(show?View.INVISIBLE:View.VISIBLE);}updateFavouriteRow();if(clock!=null&&show)clock.setTextColor(Color.WHITE);if(changed&&dockContainer!=null&&dockContainer.getHeight()>0){final boolean opening=show;final int transition=++navigationVersion,layoutGeneration=generation;final android.view.ViewTreeObserver observer=root.getViewTreeObserver();observer.addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){public boolean onPreDraw(){if(observer.isAlive())observer.removeOnPreDrawListener(this);if(transition!=navigationVersion||layoutGeneration!=generation||!active)return true;dockContainer.animate().cancel();appScroll.animate().cancel();appScroll.setAlpha(1f);appScroll.setTranslationY(0);int[] now=new int[2];dockContainer.getLocationOnScreen(now);dockContainer.setTranslationY(old[1]-now[1]);dockContainer.animate().translationY(0).setDuration(260).setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();if(opening){appScroll.setAlpha(0);appScroll.setTranslationY(dp(26));appScroll.animate().alpha(1).translationY(0).setDuration(260).start();}return true;}});}}
    App find(String pkg){for(App a:apps)if(a.pkg.equals(pkg))return a;return null;}
    TextView text(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(font);return t;}
    GradientDrawable background(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));if(stroke!=0)d.setStroke(dp(2),stroke);return d;}
    LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    void render(){
        final String desiredFocus=lastFocus;if(shelf!=null)shelf.stop();if(moveAnimation!=null)moveAnimation.cancel();generation++;final int build=generation;top.clear();dock.clear();grid.clear();
        FrameLayout stage=new FrameLayout(this);stage.setClipChildren(false);stage.setClipToPadding(false);stage.setBackgroundColor(Color.BLACK);setContentView(stage);
        shelf=new Shelf();shelf.selected=find(desiredFocus);if(shelf.selected==null&&!apps.isEmpty())shelf.selected=apps.get(0);stage.addView(shelf,new FrameLayout.LayoutParams(-1,-1));
        root=vertical();root.setClipChildren(false);root.setClipToPadding(false);root.setPadding(dp(38),dp(26),dp(38),dp(16));stage.addView(root,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);clock=text("",12,0xffe6eaf2);date=text("",13,Color.WHITE);greeting=text("",13,Color.WHITE);bar.addView(clock,new LinearLayout.LayoutParams(0,dp(30),1));
        network=null;clock.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);root.addView(bar);
        heroSpace=new View(this);heroSpace.setFocusable(false);root.addView(heroSpace,new LinearLayout.LayoutParams(-1,0,1));
        favouriteKey="";favouriteTiles.clear();favouriteScroll=new HorizontalScrollView(this);favouriteScroll.setHorizontalScrollBarEnabled(false);favouriteScroll.setClipChildren(false);favouriteScroll.setClipToPadding(false);favouriteScroll.setPadding(dp(10),dp(10),dp(10),dp(10));favouriteScroll.setFocusable(false);favouriteRow=new LinearLayout(this);favouriteRow.setClipChildren(false);favouriteRow.setClipToPadding(false);favouriteScroll.addView(favouriteRow);favouriteScroll.setVisibility(View.GONE);root.addView(favouriteScroll,new LinearLayout.LayoutParams(-1,dp(125)));
        LinearLayout dockRow=new LinearLayout(this);dockContainer=dockRow;dockRow.setClipChildren(false);dockRow.setClipToPadding(false);dockRow.setGravity(Gravity.CENTER_VERTICAL);dockRow.setPadding(dp(4),dp(8),dp(4),dp(4));dockRow.setBackground(background(0x3310141c,0));
        for(int i=0;i<Math.min(COLUMNS,apps.size());i++){View tile=tile(apps.get(i),true);dock.add(tile);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(94),1);lp.setMargins(dp(7),dp(5),dp(7),dp(5));dockRow.addView(tile,lp);}root.addView(dockRow);
        TextView gridTitle=text(moving?"MOVE APP  ·  Arrows to move  ·  Select to place  ·  Back to cancel":"YOUR APPS",11,0xffc6ccda);gridTitle.setPadding(dp(6),dp(14),0,dp(8));root.addView(gridTitle);appHeading=gridTitle;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setPadding(dp(4),dp(5),dp(4),dp(10));scroll.setFocusable(false);scroll.setVerticalScrollBarEnabled(false);LinearLayout rows=vertical();gridContainer=rows;rows.setClipChildren(false);rows.setClipToPadding(false);scroll.setSmoothScrollingEnabled(true);scroll.setClipChildren(false);scroll.addView(rows);
        LinearLayout row=null;int n=0;for(int i=COLUMNS;i<apps.size();i++){if(n%COLUMNS==0){row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setClipChildren(false);row.setClipToPadding(false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(104));rp.bottomMargin=dp(12);rows.addView(row,rp);}View tile=tile(apps.get(i),false);grid.add(tile);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(dp(7),dp(5),dp(7),dp(5));row.addView(tile,lp);n++;}
        if(row!=null&&n%COLUMNS!=0)for(int i=n%COLUMNS;i<COLUMNS;i++){View spacer=new View(this);row.addView(spacer,spacerParams());}
        appScroll=scroll;root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));TextView hint=text(moving?"Moving "+(find(movingPackage)==null?"app":find(movingPackage).label):"",10,0xffc1c8d5);hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(8),0,0);root.addView(hint);footer=hint;
        wireNavigation();updateClock();updateNetwork();setBrowsing(browsing);shelf.showItem();
        root.postDelayed(()->{if(build!=generation||!active||isDestroyed())return;View chosen=null;for(View v:dock)if(desiredFocus.equals(v.getTag()))chosen=v;for(View v:grid)if(desiredFocus.equals(v.getTag()))chosen=v;for(View v:top)if(desiredFocus.equals(v.getTag()))chosen=v;if(chosen==null&&!dock.isEmpty())chosen=dock.get(0);if(chosen!=null)chosen.requestFocus();if(moving&&chosen!=null){chosen.setBackground(background(Color.TRANSPARENT,0));chosen.setForeground(new GlossOutline());moveAnimation=android.animation.ObjectAnimator.ofFloat(chosen,"rotation",-.8f,.8f);moveAnimation.setDuration(170);moveAnimation.setRepeatCount(-1);moveAnimation.setRepeatMode(2);moveAnimation.start();}},70);
    }
    Button button(String label,Runnable action){
        Button b=new Button(this);b.setId(View.generateViewId());b.setText(label);b.setTextSize(12);b.setAllCaps(false);b.setTypeface(font);b.setTextColor(Color.WHITE);b.setPadding(dp(8),0,dp(8),0);b.setTag(label);b.setBackground(background(0xff1c222b,0));b.setOnClickListener(v->action.run());focus(b,0xff1c222b);return b;
    }
    class AppTile extends LinearLayout {
        AppTile(){super(MainActivity.this);setOrientation(VERTICAL);setClipChildren(false);setClipToPadding(false);}
        protected void onMeasure(int width,int height){int w=View.MeasureSpec.getSize(width);super.onMeasure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(Math.round(w*9f/16f),View.MeasureSpec.EXACTLY));}
    }
    class GlossOutline extends android.graphics.drawable.Drawable {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        public void draw(Canvas canvas){android.graphics.Rect bounds=getBounds();float stroke=getResources().getDisplayMetrics().density;float inset=stroke*.5f;RectF edge=new RectF(bounds.left+inset,bounds.top+inset,bounds.right-inset,bounds.bottom-inset);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setShader(new LinearGradient(0,edge.top,0,edge.bottom,new int[]{0xffffffff,0xe6ffffff,0xaaffffff},null,Shader.TileMode.CLAMP));float radius=dp(14)-inset;canvas.drawRoundRect(edge,radius,radius,paint);paint.setShader(null);}
        public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    void focus(View v,int base){
        v.setBackground(background(base,0));v.setFocusable(true);v.setFocusableInTouchMode(true);
        v.setOnFocusChangeListener((view,focused)->{view.animate().cancel();view.setAlpha(1f);boolean app=find(String.valueOf(view.getTag()))!=null;view.setBackground(background(app?Color.TRANSPARENT:focused?0xff30333d:base,0));view.setForeground(focused&&app?new GlossOutline():null);view.animate().scaleX(focused?1.055f:1f).scaleY(focused?1.055f:1f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).withLayer().start();if(focused){lastFocus=String.valueOf(view.getTag());view.setElevation(0);App selected=find(lastFocus);if(selected!=null&&shelf!=null){if(!moving)setBrowsing(grid.contains(view));shelf.select(selected);}}else view.setElevation(0);});
    }
    View missingTile(int slot,Runnable action){LinearLayout l=vertical();l.setId(View.generateViewId());l.setTag("slot"+slot);l.setGravity(Gravity.CENTER);l.addView(text("Choose favourite",16,Color.WHITE));focus(l,0xff151b24);l.setOnClickListener(v->action.run());return l;}
    View tile(App a,boolean favourite){
        LinearLayout l=new AppTile();l.setId(View.generateViewId());l.setTag(a.pkg);l.setPadding(0,0,0,0);l.setGravity(Gravity.CENTER);focus(l,Color.TRANSPARENT);l.setOnLongClickListener(v->{appOptions(a.pkg);return true;});l.setOnClickListener(v->{if(moving){finishMove(false);return;}try{if(a.pkg.equals("calm.stock.home")){stockHome();return;}if(a.pkg.equals("com.amazon.tv.settings.v2")){systemSettings(false);return;}startActivity(a.launch);}catch(Exception e){Toast.makeText(this,"Cannot open "+a.label,Toast.LENGTH_LONG).show();dirty=true;}});
        ImageView picture=new ImageView(this);picture.setBackground(background(0xff151b24,0));picture.setClipToOutline(true);picture.setScaleType(ImageView.ScaleType.FIT_XY);picture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);l.addView(picture,new LinearLayout.LayoutParams(-1,0,1));
        l.setContentDescription(a.label);
        picture.setImageBitmap(appArtwork(a));
        return l;
    }
    int appColor(App a){
        String p=a.pkg.toLowerCase(Locale.ROOT);
        if(p.contains("sounds"))return 0xffed623b;if(p.contains("iplayer"))return 0xffdb237d;
        if(p.contains("youtube"))return 0xffe52d36;if(p.contains("spotify"))return 0xff168d49;
        if(p.contains("iptvx"))return 0xff7754df;if(p.contains("netflix"))return 0xffaa1025;
        if(p.contains("disney"))return 0xff1846a0;if(p.contains("prime")||p.contains("avod"))return 0xff157bb1;
        if(p.contains("itv"))return 0xff749b19;return Color.HSVToColor(new float[]{Math.floorMod(a.pkg.hashCode(),360),.48f,.48f});
    }
    String brand(App a){String p=a.pkg.toLowerCase(Locale.ROOT);if(p.contains("sounds"))return "BBC Sounds";if(p.contains("iplayer"))return "BBC iPlayer";if(p.contains("youtube"))return "YouTube";if(p.contains("spotify"))return "Spotify";return a.label;}
    Bitmap appArtwork(App app){
        Bitmap cached=images.get(app.pkg);if(cached!=null)return cached;if(app.pkg.equals("com.amazon.tv.settings.v2")){Bitmap icon=TileArtwork.settings();images.put(app.pkg,icon);return icon;}if(app.pkg.equals("calm.stock.home")){Bitmap icon=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(icon);c.drawColor(0xff15202c);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setTypeface(Typeface.create(font,Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(43);p.setColor(Color.WHITE);c.drawText("fire tv",160,100,p);p.setColor(0xffff9900);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setStrokeCap(Paint.Cap.ROUND);c.drawArc(new RectF(101,94,219,127),15,150,false,p);images.put(app.pkg,icon);return icon;}PackageManager pm=getPackageManager();Drawable source=null;
        try{if(app.info!=null)source=app.info.activityInfo.loadBanner(pm);if(source==null&&!app.pkg.equals("com.amazon.tv.settings.v2"))source=pm.getApplicationBanner(app.pkg);}catch(Exception ignored){}
        if(source==null){try{if(app.pkg.equals("com.amazon.tv.settings.v2"))source=pm.getActivityIcon(new ComponentName("com.amazon.tv.launcher","com.amazon.tv.launcher.ui.MainSettingsActivity"));else if(app.info!=null)source=app.info.loadIcon(pm);else source=pm.getApplicationIcon(app.pkg);}catch(Exception ignored){}}
        if(source!=null&&(app.pkg.toLowerCase(Locale.ROOT).contains("bbc")||source.getIntrinsicWidth()<source.getIntrinsicHeight()*1.4f)){Bitmap fitted=TileArtwork.fitted(source,app.pkg.equals("com.amazon.cloud9"));images.put(app.pkg,fitted);return fitted;}
        Bitmap bitmap=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xff161a22);
        if(source!=null){source=source.mutate();int width=source.getIntrinsicWidth(),height=source.getIntrinsicHeight();if(width<=0||height<=0){width=height=180;}float scale=Math.min(320f/width,180f/height);int w=Math.round(width*scale),h=Math.round(height*scale);source.setBounds((320-w)/2,(180-h)/2,(320+w)/2,(180+h)/2);source.draw(canvas);}else{Drawable fallback=pm.getDefaultActivityIcon();fallback.setBounds(70,0,250,180);fallback.draw(canvas);}
        images.put(app.pkg,bitmap);return bitmap;
    }
    int blend(int a,int b,float f){return Color.rgb((int)(Color.red(a)*(1-f)+Color.red(b)*f),(int)(Color.green(a)*(1-f)+Color.green(b)*f),(int)(Color.blue(a)*(1-f)+Color.blue(b)*f));}
    ShelfRepository.Result shelfData=new ShelfRepository.Result();boolean shelfLoading=false;
    final LruCache<String,Bitmap> shelfImages=new LruCache<String,Bitmap>(12288){protected int sizeOf(String k,Bitmap b){return b.getByteCount()/1024;}};
    void refreshShelf(){
        if(shelfLoading||!active||isDestroyed())return;shelfLoading=true;
        feedWorker.execute(()->{ShelfRepository.Result result=null;ArrayList<ShelfRepository.Entry> favourites=null;try{result=ShelfRepository.load(this);favourites=FavouriteRepository.load(this);}catch(RuntimeException e){android.util.Log.w("CalmHome","Feed refresh failed: "+e.getClass().getSimpleName());}final ShelfRepository.Result refreshed=result;final ArrayList<ShelfRepository.Entry> stations=favourites;runOnUiThread(()->{shelfLoading=false;if(isDestroyed())return;if(refreshed!=null)shelfData=refreshed;if(stations!=null)stationFavourites=stations;if(active&&shelf!=null)shelf.showItem();});});
    }
    void updateFavouriteRow(){
        if(favouriteScroll==null||shelf==null||shelf.selected==null||!active)return;
        ArrayList<ShelfRepository.Entry> entries=new ArrayList<>();for(ShelfRepository.Entry e:stationFavourites)if(e.appPackage.equals(shelf.selected.pkg))entries.add(e);
        boolean rowHadFocus=favouriteScroll.hasFocus();boolean show=!browsing&&!moving&&!entries.isEmpty();favouriteScroll.setVisibility(show?View.VISIBLE:View.GONE);boolean hide=browsing||show;shelf.setVisibility(hide?View.INVISIBLE:View.VISIBLE);shelf.setAlpha(hide?0f:1f);if(hide&&clock!=null)clock.setTextColor(Color.WHITE);
        if(!show){if(rowHadFocus&&!dock.isEmpty())dock.get(Math.min(shelf.ownerSlot(),dock.size()-1)).requestFocus();return;}
        int iconWidth=!dock.isEmpty()?dock.get(0).getWidth():0;if(iconWidth<=0){final int layoutGeneration=generation;favouriteScroll.postDelayed(()->{if(!isDestroyed()&&active&&layoutGeneration==generation)updateFavouriteRow();},16);return;}
        int width=Math.round(iconWidth*4f/3f),height=Math.round(width*9f/16f);StringBuilder key=new StringBuilder(shelf.selected.pkg+":"+width);for(ShelfRepository.Entry e:entries)key.append("|").append(e.id).append(e.art).append(e.title).append(e.action.toUri(0));
        if(key.toString().equals(favouriteKey)){for(int i=0;i<Math.min(entries.size(),favouriteTiles.size());i++)bindFavouriteArtwork((ImageView)((FrameLayout)favouriteTiles.get(i)).getChildAt(0),entries.get(i));return;}boolean wasFocused=favouriteScroll.hasFocus();String focusedId="";for(View old:favouriteTiles)if(old.isFocused())focusedId=String.valueOf(old.getTag());favouriteKey=key.toString();favouriteTiles.clear();favouriteRow.removeAllViews();favouriteScroll.getLayoutParams().height=height+dp(24);favouriteScroll.requestLayout();
        for(ShelfRepository.Entry e:entries){FrameLayout tile=new FrameLayout(this);tile.setFocusable(true);tile.setTag(e.id);tile.setContentDescription(e.title);tile.setBackground(background(0xff171a20,0));tile.setClipToOutline(true);ImageView image=new ImageView(this);image.setTag(e.art);image.setScaleType(ImageView.ScaleType.CENTER_CROP);tile.addView(image,new FrameLayout.LayoutParams(-1,-1));Bitmap cached=shelfImages.get(e.art);if(cached!=null)image.setImageBitmap(cached);
            TextView label=text(e.title,12,Color.WHITE);label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setPadding(dp(10),dp(12),dp(10),dp(8));label.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00101010,0xe6101010}));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);tile.addView(label,lp);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(width,height);size.setMargins(dp(7),dp(2),dp(7),dp(2));favouriteRow.addView(tile,size);favouriteTiles.add(tile);
            tile.setOnFocusChangeListener((v,focused)->{v.setForeground(focused?new GlossOutline():null);v.animate().scaleX(focused?1.055f:1f).scaleY(focused?1.055f:1f).setDuration(180).start();});
            tile.setOnClickListener(v->{try{startActivity(new Intent(e.action));}catch(Exception ignored){Toast.makeText(this,"This favourite is unavailable",Toast.LENGTH_SHORT).show();}});
            tile.setOnKeyListener((v,k,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;int at=favouriteTiles.indexOf(v);if(k==20||k==4){dock.get(shelf.ownerSlot()).requestFocus();return true;}if(k==21||k==22){favouriteTiles.get(Math.max(0,Math.min(favouriteTiles.size()-1,at+(k==21?-1:1)))).requestFocus();return true;}return k==19;});
            bindFavouriteArtwork(image,e);
        }if(wasFocused&&!favouriteTiles.isEmpty()){View restore=favouriteTiles.get(0);for(View candidate:favouriteTiles)if(focusedId.equals(candidate.getTag()))restore=candidate;restore.requestFocus();}
    }
    void bindFavouriteArtwork(ImageView image,ShelfRepository.Entry entry){
        String art=entry.art;if(art==null||art.isEmpty())return;image.setTag(art);Bitmap cached=shelfImages.get(art);if(cached!=null){image.setImageBitmap(cached);return;}
        long now=android.os.SystemClock.elapsedRealtime();Long attempted=favouriteAttempts.get(art);if(!active||favouriteLoading.contains(art)||(attempted!=null&&now-attempted<30000))return;
        if(favouriteAttempts.size()>160)favouriteAttempts.clear();favouriteAttempts.put(art,now);favouriteLoading.add(art);
        worker.execute(()->{boolean requested=active;Bitmap loaded=requested?ShelfRepository.artwork(this,art,640,360):null;if(loaded!=null)shelfImages.put(art,loaded);runOnUiThread(()->{favouriteLoading.remove(art);if(isDestroyed())return;if(loaded==null){if(!requested){favouriteAttempts.remove(art);if(active)updateFavouriteRow();}return;}for(View tile:favouriteTiles){ImageView picture=(ImageView)((FrameLayout)tile).getChildAt(0);if(art.equals(picture.getTag()))picture.setImageBitmap(loaded);}});});
    }
    class Shelf extends FrameLayout {
        App selected;int index=0;boolean expanded=false;String imageKey="";
        final ImageView art,previousArt;View shade;String displayedKey="";final Set<String> loadingArt=new HashSet<>();final TextView section,title,facts,summary,actions,counter;final ProgressBar progress;final Handler timer=new Handler(Looper.getMainLooper());
        final Runnable advance=()->{if(!isFocused()&&entries().size()>1){index=(index+1)%entries().size();showItem();}};
        Shelf(){super(MainActivity.this);setTag("shelf");setFocusable(true);setFocusableInTouchMode(true);setBackgroundColor(0xff0e1726);
            previousArt=new ImageView(MainActivity.this);previousArt.setScaleType(ImageView.ScaleType.CENTER_CROP);addView(previousArt,new FrameLayout.LayoutParams(-1,-1));art=new ImageView(MainActivity.this);art.setScaleType(ImageView.ScaleType.CENTER_CROP);addView(art,new FrameLayout.LayoutParams(-1,-1));
            shade=new View(MainActivity.this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xdd070910,0x99070910,0x10070910}));addView(shade,new FrameLayout.LayoutParams(-1,-1));
            View bottomShade=new View(MainActivity.this);bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00070910,0x00070910,0xee070910}));addView(bottomShade,new FrameLayout.LayoutParams(-1,-1));LinearLayout content=vertical();content.setGravity(Gravity.CENTER_VERTICAL);content.setPadding(dp(48),dp(50),dp(26),dp(135));addView(content,new FrameLayout.LayoutParams(-1,-1));
            section=text("",10,0xffb7bfce);content.addView(section);title=text("",34,Color.WHITE);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setTypeface(Typeface.create(font,Typeface.BOLD));content.addView(title,new LinearLayout.LayoutParams(dp(510),-2));
            facts=text("",11,0xffd1d5df);content.addView(facts);summary=text("",12,0xffd7dbe4);summary.setMaxLines(3);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);content.addView(summary,new LinearLayout.LayoutParams(dp(540),-2));
            progress=new ProgressBar(MainActivity.this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(1000);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(240),dp(3));pp.topMargin=dp(8);content.addView(progress,pp);
            actions=text("",11,Color.WHITE);actions.setPadding(0,dp(8),0,0);content.addView(actions);
            counter=text("",11,0xffd6dae5);counter.setGravity(Gravity.RIGHT|Gravity.BOTTOM);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-1);cp.setMargins(0,0,dp(24),dp(15));addView(counter,cp);
            setOnFocusChangeListener((v,f)->{expanded=f;if(f)setBrowsing(false);if(dockContainer!=null)dockContainer.setAlpha(f?0f:1f);showItem();});
            setOnKeyListener((v,key,event)->{
                if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
                if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){int size=entries().size();if(size>1){index=Math.floorMod(index+(key==21?-1:1),size);showItem();}return true;}
                if(key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_BACK){dock.get(ownerSlot()).requestFocus();return true;}
                if(key==KeyEvent.KEYCODE_DPAD_UP)return true;
                if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){details();return true;}
                if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PLAY){play();return true;}return false;
            });setOnClickListener(v->details());
        }
        int ownerSlot(){if(selected!=null){String[] f=favourites();for(int i=0;i<f.length;i++)if(f[i].equals(selected.pkg))return i;}return 0;}
        java.util.List<ShelfRepository.Entry> entries(){ArrayList<ShelfRepository.Entry> list=new ArrayList<>();if(selected!=null)for(ShelfRepository.Entry entry:shelfData.entries)if(selected.pkg.equals(entry.appPackage))list.add(entry);return list;}
        void select(App a){if(selected==null||!selected.pkg.equals(a.pkg))index=0;selected=a;showItem();}
        void stop(){timer.removeCallbacks(advance);}
        void showItem(){
            stop();if(!active||selected==null)return;if(browsing){updateFavouriteRow();return;}updateFavouriteRow();if(favouriteScroll!=null&&favouriteScroll.getVisibility()==View.VISIBLE){setFocusable(false);return;}java.util.List<ShelfRepository.Entry> list=entries();boolean has=!list.isEmpty();index=has?Math.min(index,list.size()-1):0;
            ShelfRepository.Entry e=has?list.get(index):null;
            section.setText(has?(e.section.toUpperCase(Locale.ROOT)+" · "+selected.label.toUpperCase(Locale.ROOT)):"");title.setText(has?e.title:brand(selected));title.setVisibility(has?View.VISIBLE:View.GONE);
            ArrayList<String> meta=new ArrayList<>();if(has){if(!e.year.isEmpty())meta.add(e.year);if(e.duration>0){long minutes=e.duration/60000;meta.add(minutes>=60?minutes/60+"h "+minutes%60+"m":minutes+"m");}if(!e.genre.isEmpty())meta.add(e.genre.replace(',', '·'));}
            facts.setText(android.text.TextUtils.join("  ·  ",meta));facts.setVisibility(has&&!meta.isEmpty()?View.VISIBLE:View.GONE);
            summary.setText(has?e.summary:(ShelfRepository.FRAME.equals(selected.pkg)?shelfData.states.get(ShelfRepository.FRAME):ShelfRepository.IPTVX.equals(selected.pkg)?shelfData.state:"Open "+selected.label));summary.setVisibility(has&&expanded?View.VISIBLE:View.GONE);
            actions.setVisibility(expanded?View.VISIBLE:View.GONE);actions.setText(expanded?(has?"← → Browse    Select: Open in app    Play/Pause: Play    ↓ Apps":"Select: Open "+selected.label+"    ↓ Apps"):(has?"↑ Explore programmes":"↑ Explore    Select app below to open"));
            counter.setText(has?(index+1)+" / "+list.size():"");progress.setVisibility(has&&e.position>0&&e.duration>0?View.VISIBLE:View.GONE);if(has&&e.duration>0)progress.setProgress((int)Math.min(1000,1000.0*e.position/e.duration));
            setContentDescription(has?e.title+". "+facts.getText()+". "+(index+1)+" of "+list.size():selected.label+" top shelf. "+summary.getText());
            String key=has?e.art:"app:"+selected.pkg;imageKey=key;String pkg=selected.pkg;Bitmap media=has?shelfImages.get(key):null,brand=heroImages.get(pkg);boolean mediaArt=media!=null;
            if(clock!=null){Bitmap sample=mediaArt?media:brand;int color=sample!=null?sample.getPixel(Math.min(sample.getWidth()-1,(int)(sample.getWidth()*.94f)),Math.min(sample.getHeight()-1,(int)(sample.getHeight()*.08f))):pkg.contains("sounds")?0xffed4f16:0xff16242d;boolean light=!browsing&&Color.luminance(color)>.18f;clock.setTextColor(light?0xff20242b:0xfff1f4fa);clock.setShadowLayer(dp(1),0,dp(.5f),light?0x55ffffff:0x66000000);}
            shade.setVisibility(has?View.VISIBLE:View.GONE);setFocusable(has);setFocusableInTouchMode(has);
            Drawable picture=mediaArt?new BitmapDrawable(getResources(),media):brand!=null?new BitmapDrawable(getResources(),brand):new BrandBackdrop(pkg,brand(selected),font,appColor(selected));present(picture,mediaArt?"media:"+key:brand!=null?"brand:"+pkg:"native:"+pkg);
            if(!browsing&&brand==null&&heroAttempted.add(pkg))heroWorker.execute(()->{Bitmap loaded=null;try{BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.ARGB_8888;String asset=(pkg.equals("com.amazon.amazonvideo.livingroom")||pkg.equals("com.amazon.firebat"))?"com.amazon.avod":pkg;for(String extension:new String[]{".png",".jpg"}){try(java.io.InputStream input=getAssets().open("heroes/"+asset+extension)){loaded=BitmapFactory.decodeStream(input,null,options);if(loaded!=null)break;}catch(java.io.IOException ignored){}}}catch(Exception ignored){}final Bitmap image=loaded;if(image!=null)heroImages.put(pkg,image);runOnUiThread(()->{if(image!=null)heroAttempted.remove(pkg);if(!isDestroyed()&&shelf==this&&selected!=null&&pkg.equals(selected.pkg))showItem();});});
            if(!browsing&&has&&!mediaArt&&!key.isEmpty()&&loadingArt.add(key))worker.execute(()->{Bitmap loaded=active&&!browsing?ShelfRepository.artwork(MainActivity.this,key):null;if(loaded!=null)shelfImages.put(key,loaded);runOnUiThread(()->{loadingArt.remove(key);if(loaded!=null&&!isDestroyed()&&shelf==this&&key.equals(imageKey))showItem();});});
            updateFavouriteRow();if(active&&!browsing&&prefs.getBoolean("artworkRotation",false)&&has&&!expanded&&list.size()>1)timer.postDelayed(advance,8000);
        }
        void present(Drawable image,String key){if(key.equals(displayedKey))return;art.animate().cancel();previousArt.animate().cancel();Drawable old=art.getDrawable();previousArt.setImageDrawable(old);previousArt.setPadding(0,0,0,0);previousArt.setScaleType(ImageView.ScaleType.CENTER_CROP);previousArt.setImageAlpha(255);previousArt.setAlpha(old==null?0f:1f);art.setPadding(0,0,0,0);art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setImageDrawable(image);art.setImageAlpha(255);art.setAlpha(old==null?1f:0f);displayedKey=key;if(old!=null){previousArt.animate().alpha(0f).setDuration(220).withEndAction(()->previousArt.setImageDrawable(null)).start();art.animate().alpha(1f).setDuration(220).start();}}
        void details(){java.util.List<ShelfRepository.Entry> list=entries();if(list.isEmpty()){play();return;}ShelfRepository.Entry entry=list.get(index);try{Intent destination=new Intent(entry.action);Uri uri=destination.getData();if(ShelfRepository.FRAME.equals(entry.appPackage)&&uri!=null&&"framefire".equals(uri.getScheme())&&"movie".equals(uri.getHost()))destination.setData(uri.buildUpon().clearQuery().appendQueryParameter("action","details").build());startActivity(destination);}catch(Exception e){Toast.makeText(MainActivity.this,"Open this film in "+selected.label,Toast.LENGTH_LONG).show();}}
        void play(){try{java.util.List<ShelfRepository.Entry> list=entries();if(!list.isEmpty())startActivity(new Intent(list.get(index).action));else if(selected!=null)startActivity(selected.launch);}catch(Exception e){Toast.makeText(MainActivity.this,"Open this programme in its app",Toast.LENGTH_LONG).show();}}
    }
    void wireNavigation(){
        ArrayList<View> all=new ArrayList<>();all.addAll(top);all.addAll(dock);all.addAll(grid);
        for(View v:all)v.setOnKeyListener((view,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
            if(key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN&&key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;
            int i=top.indexOf(view);View next=view;
            if(i>=0){if(key==21)next=top.get(Math.max(0,i-1));if(key==22)next=top.get(Math.min(top.size()-1,i+1));if(key==20)next=shelf;}
            else if((i=dock.indexOf(view))>=0){if(key==21)next=dock.get(Math.max(0,i-1));if(key==22)next=dock.get(Math.min(dock.size()-1,i+1));if(key==19)next=!favouriteTiles.isEmpty()&&favouriteScroll.getVisibility()==View.VISIBLE?favouriteTiles.get(0):shelf;if(key==20&&!grid.isEmpty()){setBrowsing(true);next=grid.get(Math.min(i,grid.size()-1));}}
            else if((i=grid.indexOf(view))>=0){int col=i%COLUMNS;int row=i/COLUMNS;if(key==21&&col>0)next=grid.get(i-1);if(key==22&&col<COLUMNS-1&&i+1<grid.size())next=grid.get(i+1);if(key==19)next=row==0?dock.get(col):grid.get(i-COLUMNS);if(key==20&&(row+1)*COLUMNS<grid.size())next=grid.get(Math.min(i+COLUMNS,grid.size()-1));}
            next.requestFocus();return true;
        });
    }
    void updateClock(){if(clock==null)return;Date now=new Date();clock.setText(new SimpleDateFormat("HH:mm",Locale.getDefault()).format(now));date.setText(new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(now));int h=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);greeting.setText(h<12?"Good morning":h<18?"Good afternoon":"Good evening");}
    void updateNetwork(){if(network==null)return;Network n=connectivity.getActiveNetwork();NetworkCapabilities c=n==null?null:connectivity.getNetworkCapabilities(n);LinkProperties lp=n==null?null:connectivity.getLinkProperties(n);android.util.Log.i("CalmHome","network validated="+(c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))+" privateDnsActive="+(lp!=null&&lp.isPrivateDnsActive())+" privateDnsName="+(lp==null?"":lp.getPrivateDnsServerName()));network.setText(c==null?"Offline":c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?"Online":"Network");}
    void systemSettings(boolean net){
        if(!net){try{startActivity(new Intent().setComponent(new ComponentName("com.amazon.tv.launcher","com.amazon.tv.launcher.ui.MainSettingsActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));return;}catch(Exception ignored){}}
        for(String action:net?new String[]{Settings.ACTION_WIFI_SETTINGS,Settings.ACTION_WIRELESS_SETTINGS,Settings.ACTION_SETTINGS}:new String[]{Settings.ACTION_SETTINGS}){
            try{startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));return;}catch(Exception ignored){}
        }
        try{startActivity(new Intent().setComponent(new ComponentName("com.amazon.tv.settings.v2","com.amazon.tv.settings.v2.SettingsActivity")));}catch(Exception e){Toast.makeText(this,"Open Fire TV Settings from Amazon Home",Toast.LENGTH_LONG).show();}
    }
    void stockHome(){try{startActivity(new Intent().setComponent(new ComponentName("io.github.toolicious.homeonfire","io.github.toolicious.homeonfire.StockHomeActivity")).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));}catch(Exception e){Toast.makeText(this,"Hold Home to open Fire OS",Toast.LENGTH_LONG).show();}}
    void configure(){ArrayList<String> choices=new ArrayList<>();for(int i=0;i<Math.min(COLUMNS,apps.size());i++)choices.add((i+1)+" · "+dockLabel(i));choices.add("Appearance / static wallpaper");choices.add("Refresh Top Shelf");choices.add("Restore hidden apps");choices.add("Performance");final int slots=Math.min(COLUMNS,apps.size());new AlertDialog.Builder(this).setTitle("Configure Calm Home").setItems(choices.toArray(new String[0]),(d,n)->{if(n<slots)chooseDock(n);else if(n==slots)appearance();else if(n==slots+1)refreshShelf();else if(n==slots+2)restoreHidden();else performance();}).setNegativeButton("Done",null).show();}
    void performance(){boolean rotation=prefs.getBoolean("artworkRotation",false);new AlertDialog.Builder(this).setTitle("Performance").setItems(new String[]{"Automatic artwork rotation: "+(rotation?"On":"Off"),"Fire TV settings"},(d,n)->{if(n==0){prefs.edit().putBoolean("artworkRotation",!rotation).apply();if(shelf!=null)shelf.showItem();}else systemSettings(false);}).setNegativeButton("Done",null).show();}
    public void onTrimMemory(int level){super.onTrimMemory(level);if(level==TRIM_MEMORY_RUNNING_LOW||level==TRIM_MEMORY_RUNNING_CRITICAL||level>=TRIM_MEMORY_BACKGROUND){images.evictAll();heroImages.evictAll();shelfImages.evictAll();}}
    void appOptions(String pkg){App app=find(pkg);if(app==null)return;new AlertDialog.Builder(this).setTitle(app.label).setItems(new String[]{"Move", "Remove from Calm Home"},(dialog,which)->{if(which==0)beginMove(pkg);else{if(apps.size()<=1){Toast.makeText(this,"Keep at least one app on the screen",Toast.LENGTH_SHORT).show();return;}Set<String> hidden=new HashSet<>(prefs.getStringSet("hiddenApps",Collections.emptySet()));hidden.add(pkg);prefs.edit().putStringSet("hiddenApps",hidden).apply();discover();lastFocus=apps.get(0).pkg;render();}}).setNegativeButton("Cancel",null).show();}
    void restoreHidden(){if(hiddenApps.isEmpty()){Toast.makeText(this,"No hidden apps",Toast.LENGTH_SHORT).show();return;}String[] names=new String[hiddenApps.size()];for(int i=0;i<names.length;i++)names[i]=hiddenApps.get(i).label;new AlertDialog.Builder(this).setTitle("Restore hidden apps").setItems(names,(dialog,which)->{String pkg=hiddenApps.get(which).pkg;Set<String> hidden=new HashSet<>(prefs.getStringSet("hiddenApps",Collections.emptySet()));hidden.remove(pkg);prefs.edit().putStringSet("hiddenApps",hidden).apply();discover();lastFocus=pkg;render();}).setNegativeButton("Cancel",null).show();}
    String dockLabel(int i){return i<apps.size()?apps.get(i).label:"Choose app";}
    void chooseDock(int slot){String[] labels=new String[apps.size()];for(int i=0;i<apps.size();i++)labels[i]=apps.get(i).label;new AlertDialog.Builder(this).setTitle("First-row position "+(slot+1)).setItems(labels,(d,n)->{App selected=apps.remove(n);apps.add(Math.min(slot,apps.size()),selected);saveOrder();lastFocus=selected.pkg;render();}).setNegativeButton("Cancel",null).show();}
    void appearance(){new AlertDialog.Builder(this).setTitle("Static wallpaper").setSingleChoiceItems(new String[]{"Pure black","Midnight gradient"},prefs.getString("wallpaper","black").equals("black")?0:1,(d,n)->{prefs.edit().putString("wallpaper",n==0?"black":"midnight").apply();d.dismiss();render();}).setNegativeButton("Cancel",null).show();}
}
