package uk.michael.calmhome;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import java.util.*;
import java.io.*;
import java.net.*;

/** Only reads system-published recommendations. Never opens IPTVX private files. */
final class ShelfRepository {
    static final String FRAME="uk.michael.framefire";
    static final String IPTVX="com.bendingx.iptvx.android";
    static class Entry {
        String id,title,summary,art,year,genre,appPackage,section,people;long duration,position;Intent action;
    }
    static class Result {final ArrayList<Entry> entries=new ArrayList<>();final java.util.Map<String,String> states=new java.util.HashMap<>();String state="No programmes available for Top Shelf yet";}
    static String str(Cursor c,String name){int i=c.getColumnIndex(name);String value=i<0||c.isNull(i)?"":c.getString(i);return value==null||value.equalsIgnoreCase("null")?"":value;}
    static long number(Cursor c,String name){try{return Long.parseLong(str(c,name));}catch(Exception e){return 0;}}
    static String limited(String s,int n){return s.length()>n?s.substring(0,n):s;}
    static Intent ownedAction(Context context,String raw,String owner){try{if(raw.isEmpty()||raw.length()>8192)return null;Intent intent=Intent.parseUri(raw,Intent.URI_INTENT_SCHEME);intent.setSelector(null);intent.setClipData(null);intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);intent.setPackage(owner);if(intent.getComponent()!=null&&!owner.equals(intent.getComponent().getPackageName()))return null;android.content.pm.ResolveInfo resolved=context.getPackageManager().resolveActivity(intent,0);return resolved!=null&&resolved.activityInfo.exported&&owner.equals(resolved.activityInfo.packageName)?intent:null;}catch(Exception ignored){return null;}}
    static Result load(Context context){
        Result result=new Result();Set<String> seen=new HashSet<>();boolean readable=false,denied=false;
        for(String table:new String[]{"frame_recent","watch_next_program","preview_program"}){
            String owner=table.equals("frame_recent")?FRAME:IPTVX;String endpoint=owner.equals(FRAME)?"content://uk.michael.framefire.shelf/recent":"content://android.media.tv/"+table;
            try(Cursor c=context.getContentResolver().query(Uri.parse(endpoint),null,null,null,null)){
                readable=true;if(c==null)continue;
                int scanned=0;while(scanned++<2000&&c.moveToNext()&&result.entries.size()<20){
                    if(!owner.equals(str(c,"package_name"))||"0".equals(str(c,"browsable")))continue;
                    String title=str(c,"title"),raw=str(c,"intent_uri");if(title.isEmpty()||raw.isEmpty()||raw.length()>8192)continue;
                    Intent intent=ownedAction(context,raw,owner);if(intent==null)continue;
                    Entry e=new Entry();e.id=table+":"+str(c,"_id");if(!seen.add(raw))continue;
                    e.appPackage=owner;e.section=owner.equals(FRAME)?"Recently Added":"Continue Watching";e.people=limited(str(c,"people"),1000);e.title=limited(title,300);e.summary=limited(str(c,"short_description"),2000);e.year=str(c,"release_date");if(e.year.length()>4)e.year=e.year.substring(0,4);
                    e.genre=limited(str(c,"canonical_genre"),100);e.duration=number(c,"duration_millis");e.position=number(c,"last_playback_position_millis");e.action=intent;
                    e.art=str(c,"thumbnail_uri");if(e.art.isEmpty())e.art=str(c,"poster_art_uri");result.entries.add(e);
                }
            }catch(SecurityException e){denied=true;android.util.Log.i("CalmShelf","Read restriction: "+e.getMessage());}catch(Exception e){android.util.Log.i("CalmShelf","Provider unavailable: "+table+" "+e.getClass().getSimpleName());}
        }
        if(result.entries.stream().anyMatch(e->IPTVX.equals(e.appPackage)))result.state="From IPTVX";else if(denied&&!readable)result.state="Fire OS restricts access to shared programmes";
        else if(!readable)result.state="Shared programme feed unavailable on this Fire TV";
        android.util.Log.i("CalmShelf","entries="+result.entries.size()+" readable="+readable+" denied="+denied);result.states.put(FRAME,"No recent movies shared by Frame yet");return result;
    }
    static Bitmap artwork(Context context,String value){return artwork(context,value,1920,1080);}
    static Bitmap artwork(Context context,String value,int maxWidth,int maxHeight){
        try{
            Uri uri=Uri.parse(value);InputStream input;
            if("content".equals(uri.getScheme())&&("com.bendingx.iptvx.android.watchnext.artwork".equals(uri.getAuthority())||"uk.michael.framefire.shelf".equals(uri.getAuthority())||FavouriteRepository.publishedArtwork(context,uri)))input=context.getContentResolver().openInputStream(uri);
            else if("https".equals(uri.getScheme())&&uri.getUserInfo()==null){URLConnection connection=new URL(value).openConnection();connection.setConnectTimeout(4000);connection.setReadTimeout(4000);input=connection.getInputStream();}
            else return null;
            if(input==null)return null;byte[] bytes;
            try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
                long deadline=android.os.SystemClock.elapsedRealtime()+12000;byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted()||android.os.SystemClock.elapsedRealtime()>deadline)return null;if(out.size()+n>4*1024*1024)return null;out.write(buffer,0,n);}bytes=out.toByteArray();
            }
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            if(options.outWidth<=0||options.outHeight<=0||options.outWidth>16000||options.outHeight>16000)return null;
            options.inSampleSize=1;while(options.outWidth/options.inSampleSize>maxWidth||options.outHeight/options.inSampleSize>maxHeight)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        }catch(Exception e){return null;}
    }
}
