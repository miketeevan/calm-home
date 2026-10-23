package io.github.toolicious.homeonfire;
/** Signature-protected shortcut to the helper's existing stock-Home escape. */
public final class StockHomeActivity extends android.app.Activity {
 public void onCreate(android.os.Bundle state){super.onCreate(state);if(!HijackService.openStockHomeFromTile()){HijackService.requestBypass(5000);try{startActivity(new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME).setPackage("com.amazon.tv.launcher").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Exception ignored){}}finish();}
}
