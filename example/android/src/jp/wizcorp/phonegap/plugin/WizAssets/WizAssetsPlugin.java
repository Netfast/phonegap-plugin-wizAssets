/*
 *  __    __ _                  _     _                _                                                  ___ _             _       
 * / / /\ \ (_)______ _ _ __ __| |   /_\  ___ ___  ___| |_    /\/\   __ _ _ __   __ _  __ _  ___ _ __    / _ \ |_   _  __ _(_)_ __  
 * \ \/  \/ / |_  / _` | '__/ _` |  //_\\/ __/ __|/ _ \ __|  /    \ / _` | '_ \ / _` |/ _` |/ _ \ '__|  / /_)/ | | | |/ _` | | '_ \ 
 *  \  /\  /| |/ / (_| | | | (_| | /  _  \__ \__ \  __/ |_  / /\/\ \ (_| | | | | (_| | (_| |  __/ |    / ___/| | |_| | (_| | | | | |
 *   \/  \/ |_/___\__,_|_|  \__,_| \_/ \_/___/___/\___|\__| \/    \/\__,_|_| |_|\__,_|\__, |\___|_|    \/    |_|\__,_|\__, |_|_| |_|
 *                                                                                    |___/                           |___/        
 * @author 	Ally Ogilvie  
 * @copyright Wizcorp Inc. [ Incorporated Wizards ] 2012
 * @file	- wizAssetManagerPlugin.java
 * @about	- Handle JavaScript API calls from PhoneGap to WizAssetsPlugin
*/

package jp.wizcorp.phonegap.plugin.WizAssets;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.res.Resources;
import android.util.Log;

import org.apache.cordova.api.CallbackContext;
import org.apache.cordova.api.CordovaPlugin;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;

public class WizAssetsPlugin extends CordovaPlugin {

	private String TAG = "WizAssetsPlugin";
	private WizAssetManager wizAssetMan = null;
	
	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
	
		if (wizAssetMan == null) {
			wizAssetMan = new WizAssetManager(cordova.getActivity().getApplicationContext());
		}
		
	    if ("downloadFile".equals(action)) {
	    	Log.d(TAG, "[downloadFile] *********** "+args.toString() );
			try {
				// Split by "/"
				String[] splitURL = args.getString(1).split("/");
				
				// Last element is name
				String fileName = splitURL[splitURL.length-1];

				// Build directory
				Resources appR = cordova.getActivity().getApplicationContext().getResources();
				// Dir name includes game/application name as folder root.. ie. "cordovaExample"
				CharSequence txt = appR.getText(appR.getIdentifier("app_name", "string", cordova.getActivity().getApplicationContext().getPackageName()))+"/"; 

				String dirName = ""+txt;
				for (int i=0; i<splitURL.length-1; i++) {
					dirName = dirName+splitURL[i]+"/";
				}
				downloadUrl(args.getString(0), dirName, fileName, "true", callbackContext );
			} catch (JSONException e) {
				callbackContext.error("Param errrors");
				return true;
			}

	    } else if ("getFileURI".equals(action)) {
			
			Log.d(TAG, "[getFileURI] search full file path for: "+ args.toString() );
			String asset = null;
			String relpath = null;
			
			// Get application / game name to prefix relpath
			Resources appR = cordova.getActivity().getApplicationContext().getResources();
			// Dir name includes game/application name as folder root.. ie. "cordovaExample"
			CharSequence txt = appR.getText(appR.getIdentifier("app_name", "string", cordova.getActivity().getApplicationContext().getPackageName()))+"/"; 

			String dirName = ""+txt;
			try {
				relpath = args.getString(0);
				asset = wizAssetMan.getFile(dirName + relpath);
			} catch (JSONException e) {
				Log.d(TAG, "[getFileURI] error: " + e.toString());			
				callbackContext.error(e.toString() );
			}
			
			if (asset == "" || asset == null || asset.contains("NotFoundError")) {
				callbackContext.error("NotFoundError");
			} else {
				Log.d(TAG, "[getFileURI] Returning full path: " + asset);
				callbackContext.success(asset);
			}
			return true;

		} else if ("getFileURIs".equals(action)) {
			
			// Return all assets as asset map object
			Log.d(TAG, "[getFileURIs] *********** >>>>>>> ");
			JSONObject assetObject = wizAssetMan.getAllAssets();			
	        Log.d(TAG, "[getFileURIs] RETURN *********** >>>>>>> ");
			callbackContext.success(assetObject);
			return true;
			
		} else if ("deleteFiles".equals(action)) {
			
			// Delete all files from array given
			Log.d(TAG, "[deleteFiles] *********** ");
			try {
				for (int i = 0; i<args.length(); i++) {
					
					String filepath = args.getString(i);
					// If file is in bundle we cannot delete so ignore
					if (!filepath.contains("www/assets")) {
						// Delete the file, we do not care success or fail
						// If you want to check file is deleted, delete() returns boolean 
						// so you can implement a check after delete() if necessary
						File file = new File(filepath);
						file.delete();
						// Delete from database
						wizAssetMan.deleteFile(filepath);
					}
				}
			} catch (JSONException e) {
				Log.d(TAG, "failed to parse download strings *********** "+e);
			}
			
			// We do not care about success or fail...
			callbackContext.success();
			return true;
			
		} else if (action.equals("deleteFile")) {
			
			Log.d(TAG, "[deleteFile] *********** " + args.getString(0));
			String filepath = args.getString(0);
			
			// If file is in bundle we cannot delete so ignore
			if (!filepath.contains("www/assets")) {
				// Delete the file, we do not care success or fail
				// If you want to check file is deleted, delete() returns boolean 
				// so you can implement a check after delete() if necessary
				File file = new File(filepath);
				file.delete();
				// Delete from database
				wizAssetMan.deleteFile(filepath);
			}
			
			// Callback success for any outcome.
			callbackContext.success();
			return true;
		}

	    return false;  // Returning false results in a "MethodNotFound" error.
	}

	 private void downloadUrl(String fileUrl, String dirName, String fileName, String overwrite, CallbackContext callbackContext){
		 // Download files to sdcard, or phone if sdcard not exists
		 String result;
		 try {
			 
			 String pathTostorage = cordova.getActivity().getApplicationContext().getCacheDir().getAbsolutePath() + File.separator;
			 File dir = new File(pathTostorage + dirName);
			 if (!dir.exists()) {
				 // Create the directory if not existing
				 dir.mkdirs();
			 }
	 
			 File file = new File(pathTostorage + dirName + "/" + fileName);
			 Log.d(TAG, "[downloadUrl] *********** pathTostorage pathTostorage+dirName+fileName > " + file.getAbsolutePath());

			 if (overwrite.equals("false") && file.exists()){
				 Log.d(TAG, "File already exists.");
				 result = "file already exists";
				 callbackContext.success(result);
				 return;
			 }
	 
			 URL url = new URL(fileUrl);
			 HttpGet httpRequest = null;
			 httpRequest = new HttpGet(url.toURI());
			 
			 HttpClient httpclient = new DefaultHttpClient();
			 HttpResponse response = (HttpResponse) httpclient.execute(httpRequest);
			 
			 HttpEntity entity = response.getEntity();
			 BufferedHttpEntity bufHttpEntity = new BufferedHttpEntity(entity);
			 InputStream is = bufHttpEntity.getContent();
			 
			 byte[] buffer = new byte[1024];
			 
			 int len1 = 0;
		 
		 		FileOutputStream fos = new FileOutputStream(file);
		 
			 while ( (len1 = is.read(buffer)) > 0 ) {
				 fos.write(buffer,0, len1);
			 }

			 fos.close();
			 is.close();
			 
			 result = file.getAbsolutePath();
			 
			 // Tell Asset Manager to register this download to asset database
			 wizAssetMan.downloadedAsset(dirName + fileName, file.getAbsolutePath());
		 } catch (MalformedURLException e) {
			 Log.e("WizAssetsPlugin", "bad url", e);
			 // Ignore error
		 	 result = "file:///android_asset/" + dirName + "/" + fileName;
		 } catch (Exception e) {		 
			 Log.e("WizAssetsPlugin", "io error: " + e);
			 // Ignore error
		 	 result = "file:///android_asset/" + dirName + "/" + fileName;
			 
		 }
		 callbackContext.success(result);
	}
}
