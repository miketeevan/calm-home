package uk.michael.calmhome;
import android.content.*;import android.graphics.*;import android.view.*;import java.util.*;
final class StabilityChecks {
 static void run(MoveAcceptance t)throws Exception {
  MainActivity a=t.a;ArrayList<String> before=a.currentOrder();String savedOrder=a.prefs.getString("appOrder","[]");Set<String> savedHidden=new HashSet<>(a.prefs.getStringSet("hiddenApps",Collections.emptySet()));
  try {
   Bitmap full=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);a.shelfImages.put("stability-full-hd",full);t.check(a.shelfImages.get("stability-full-hd")==full,"Full HD image fits cache without immediate eviction/reload loop");a.shelfImages.remove("stability-full-hd");
   t.check(ShelfRepository.artwork(a,"invalid artwork") == null,"Invalid artwork fails safely without affecting navigation");
   Bitmap thumb=ShelfRepository.artwork(a,"content://uk.michael.calmhome.tests.favourites/art",320,180);t.check(thumb!=null&&thumb.getWidth()<=320&&thumb.getHeight()<=180,"Favourite thumbnails decode at bounded display size");
   t.runOnMainSync(()->{a.lastFocus=before.get(0);a.browsing=false;a.render();a.render();});Thread.sleep(300);
   for(int i=0;i<35;i++){t.key(20);t.key(22);t.key(21);t.key(19);}Thread.sleep(500);
   t.check(a.dockContainer.getAlpha()==1f,"Rapid grid transitions leave dock visible");
   t.runOnMainSync(()->a.grid.get(0).requestFocus());Thread.sleep(400);t.check(a.appScroll.getAlpha()==1f&&Math.abs(a.appScroll.getTranslationY())<1,"Grid animation settles to visible resting state");t.check(a.shelf.getVisibility()==View.INVISIBLE,"Black grid does not expose background artwork");
   t.runOnMainSync(()->{a.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);a.dock.get(0).requestFocus();});Thread.sleep(1500);t.check(a.shelf.art.getDrawable()!=null&&a.dock.get(0).getAlpha()==1f,"Memory-pressure cache release leaves displayed icons and hero intact");
   String owner=a.apps.get(0).pkg;ArrayList<ShelfRepository.Entry> entries=FavouriteRepository.load(a);ArrayList<ShelfRepository.Entry> fixture=new ArrayList<>();for(ShelfRepository.Entry e:entries)if("uk.michael.calmhome.tests".equals(e.appPackage)){e.appPackage=owner;fixture.add(e);}
   t.runOnMainSync(()->{a.stationFavourites=fixture;a.shelf.select(a.find(owner));});Thread.sleep(250);t.check(a.favouriteTiles.size()==7,"Favourites row loads fixture entries");t.runOnMainSync(()->a.favouriteTiles.get(3).requestFocus());String focused=String.valueOf(a.favouriteTiles.get(3).getTag());
   t.runOnMainSync(()->{fixture.get(0).title="Updated station title";a.updateFavouriteRow();});t.check(a.getCurrentFocus()!=null&&focused.equals(a.getCurrentFocus().getTag()),"Favourites refresh preserves the selected station");
   t.runOnMainSync(()->{a.stationFavourites=new ArrayList<>();a.updateFavouriteRow();});t.check(a.dock.get(0).isFocused()&&a.dockContainer.getAlpha()==1f,"Empty favourites refresh safely restores focus to owning app");
   t.runOnMainSync(()->{a.beginMove(before.get(0));a.moveApp(22);a.startActivity(new Intent().setComponent(new ComponentName("com.amazon.tv.launcher","com.amazon.tv.launcher.ui.MainSettingsActivity")));});Thread.sleep(700);
   t.check(!a.moving&&a.currentOrder().equals(before),"Leaving Calm Home cancels an unfinished move without saving it");
   t.a=(MainActivity)t.startActivitySync(new Intent(a,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(300);MainActivity resumed=t.a;t.check(resumed.currentOrder().equals(before),"Order survives real activity handoff and recreation");
   t.runOnMainSync(()->{MainActivity.App keep=resumed.apps.get(0);resumed.apps.clear();resumed.apps.add(keep);resumed.lastFocus=keep.pkg;resumed.render();});Thread.sleep(250);for(int key:new int[]{20,22,21,19,20,4})t.key(key);t.check(resumed.dock.size()==1&&resumed.grid.isEmpty(),"Single remaining tile navigates safely without grid entries");
  } finally {
   MainActivity current=t.a;t.runOnMainSync(()->{current.prefs.edit().putString("appOrder",savedOrder).putStringSet("hiddenApps",savedHidden).apply();current.stationFavourites.clear();current.discover();current.lastFocus=before.get(0);current.browsing=false;current.render();});
  }
 }
}
