package uk.michael.calmhome;
import android.content.*;import android.content.pm.*;import android.database.Cursor;import android.net.Uri;import java.util.*;
/** Opt-in public favourites feed. No access to another app's private storage. */
final class FavouriteRepository {
 static final String META="uk.michael.calmhome.FAVOURITES";
 static boolean publishedArtwork(Context c,Uri uri){try{ProviderInfo p=c.getPackageManager().resolveContentProvider(uri.getAuthority(),PackageManager.GET_META_DATA);return p!=null&&p.exported&&p.enabled&&p.metaData!=null&&p.metaData.getBoolean(META,false);}catch(Exception e){return false;}}
 static ArrayList<ShelfRepository.Entry> load(Context context){ArrayList<ShelfRepository.Entry> out=new ArrayList<>();PackageManager pm=context.getPackageManager();
  for(PackageInfo app:pm.getInstalledPackages(PackageManager.GET_PROVIDERS|PackageManager.GET_META_DATA)){if(app.providers==null)continue;for(ProviderInfo p:app.providers){if(!p.exported||!p.enabled||p.metaData==null||!p.metaData.getBoolean(META,false)||p.authority==null)continue;
   try(Cursor c=context.getContentResolver().query(Uri.parse("content://"+p.authority.split(";")[0]+"/favourites"),null,null,null,null)){if(c==null)continue;int count=0,scanned=0;Set<String> ids=new HashSet<>();while(count<40&&scanned++<500&&c.moveToNext()){String id=ShelfRepository.str(c,"_id"),title=ShelfRepository.str(c,"title"),raw=ShelfRepository.str(c,"intent_uri");if(id.isEmpty()||title.isEmpty()||raw.isEmpty()||raw.length()>8192||!ids.add(id))continue;
    Intent action=ShelfRepository.ownedAction(context,raw,p.packageName);if(action==null)continue;
    ShelfRepository.Entry e=new ShelfRepository.Entry();e.id=p.authority+":"+id;e.title=ShelfRepository.limited(title,300);e.appPackage=p.packageName;e.action=action;e.art=ShelfRepository.str(c,"thumbnail_uri");if(e.art.isEmpty())e.art=ShelfRepository.str(c,"poster_art_uri");e.section="Favourites";out.add(e);count++;
   }}catch(Exception ignored){android.util.Log.i("CalmFavourites","Feed unavailable for "+p.packageName);}
  }}return out;
 }
}
