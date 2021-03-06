package com.adsdk.sdk;

import static com.adsdk.sdk.Const.AD_EXTRA;

import java.io.InputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.HashMap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;

import com.adsdk.sdk.video.ResourceManager;
import com.adsdk.sdk.video.RichMediaActivity;
import com.adsdk.sdk.video.RichMediaAd;
import com.adsdk.sdk.video.TrackerService;

public class AdManager {

	private static HashMap<Long, AdManager> sRunningAds = new HashMap<Long, AdManager>();

	private String mPublisherId;
	private String mUniqueId1;
	private String mUniqueId2;
	private boolean mIncludeLocation;
	private static Activity mActivity;
	private Thread mRequestThread;
	private Handler mHandler;
	private AdRequest mRequest = null;
	private AdListener mListener;
	private boolean mEnabled = true;
	private RichMediaAd mResponse;
	private String requestURL;

	private String mUserAgent;

	public static AdManager getAdManager(RichMediaAd ad) {
		AdManager adManager = sRunningAds.remove(ad.getTimestamp());
		return adManager;
	}

	public static void closeRunningAd(RichMediaAd ad, boolean result) {
		AdManager adManager = sRunningAds.remove(ad.getTimestamp());
		adManager.notifyAdClose(ad, result);
	}

	public void release() {
		TrackerService.release();
		ResourceManager.cancel();

	}

	public AdManager(Activity activity, final String requestURL, final String publisherId,
			final boolean includeLocation)
					throws IllegalArgumentException {
		AdManager.setmActivity(activity);
		this.requestURL = requestURL;
		this.mPublisherId = publisherId;
		this.mIncludeLocation = includeLocation;
		this.mRequestThread = null;
		this.mHandler = new Handler();
		initialize();
	}

	public void setListener(AdListener listener) {
		this.mListener = listener;
	}

	public void requestAd() {
		if (!mEnabled) {
			return;
		}
		if (mRequestThread == null) {
			mResponse = null;
			mRequestThread = new Thread(new Runnable() {
				@Override
				public void run() {
					while (ResourceManager.isDownloading()) {
						try {
							Thread.sleep(200);
						} catch (InterruptedException e) {
						}
					}
					try {
						RequestRichMediaAd requestAd = new RequestRichMediaAd();
						AdRequest request = getRequest();
						mResponse = requestAd.sendRequest(request);
						if(mResponse.getVideo()!=null && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.FROYO){
							notifyNoAdFound();
						}
						else if (mResponse.getType() == Const.VIDEO_TO_INTERSTITIAL || mResponse.getType() == Const.INTERSTITIAL_TO_VIDEO || mResponse.getType() == Const.VIDEO || mResponse.getType() == Const.INTERSTITIAL ) {
							if (mListener != null) {
								mHandler.post(new Runnable() {

									@Override
									public void run() {
										mListener.adLoadSucceeded(mResponse);
									}
								});
							}
						} else if (mResponse.getType() == Const.NO_AD){
							if (mListener != null) {
								mHandler.post(new Runnable() {

									@Override
									public void run() {
										notifyNoAdFound();
									}
								});
							}
						}
						else {
							if (mListener != null) {
								mHandler.post(new Runnable() {

									@Override
									public void run() {
										notifyNoAdFound();
									}
								});
							}
						}
					} catch (Throwable t) {
						mResponse = new RichMediaAd();
						mResponse.setType(Const.AD_FAILED);
						if (mListener != null) {
							t.printStackTrace();

							mHandler.post(new Runnable() {

								@Override
								public void run() {
									notifyNoAdFound();

								}
							});
						}
					}
					mRequestThread = null;
				}
			});
			mRequestThread
			.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {

				@Override
				public void uncaughtException(Thread thread,
						Throwable ex) {
					mResponse = new RichMediaAd();
					mResponse.setType(Const.AD_FAILED);
					mRequestThread = null;
				}
			});
			mRequestThread.start();
		}
	}

	public void setRequestURL(String requestURL){
		this.requestURL = requestURL;
	}

	public void requestAd(final InputStream xml) {
		if (!mEnabled) {
			return;
		}
		if (mRequestThread == null) {
			mResponse = null;
			mRequestThread = new Thread(new Runnable() {
				@Override
				public void run() {
					while (ResourceManager.isDownloading()) {
						try {
							Thread.sleep(200);
						} catch (InterruptedException e) {
						}
					}
					try {
						RequestRichMediaAd requestAd = new RequestRichMediaAd(xml);
						AdRequest request = getRequest();
						mResponse = requestAd.sendRequest(request);
						if (mResponse.getType() != Const.NO_AD) {
							if (mListener != null) {
								mHandler.post(new Runnable() {

									@Override
									public void run() {
										mListener.adLoadSucceeded(mResponse);
									}
								});
							}
						} else {
							if (mListener != null) {
								mHandler.post(new Runnable() {

									@Override
									public void run() {
										notifyNoAdFound();
									}
								});
							}
						}
					} catch (Throwable t) {
						mResponse = new RichMediaAd();
						mResponse.setType(Const.AD_FAILED);
						if (mListener != null) {

							mHandler.post(new Runnable() {

								@Override
								public void run() {
									notifyNoAdFound();

								}
							});
						}
					}
					mRequestThread = null;
				}
			});
			mRequestThread
			.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {

				@Override
				public void uncaughtException(Thread thread,
						Throwable ex) {
					mResponse = new RichMediaAd();
					mResponse.setType(Const.AD_FAILED);
					mRequestThread = null;
				}
			});
			mRequestThread.start();
		}
	}

	public boolean isAdLoaded() {
		return (mResponse != null);
	}

	public void requestAdAndShow(long timeout) {
		AdListener l = mListener;

		mListener = null;
		requestAd();
		long now = System.currentTimeMillis();
		long timeoutTime = now + timeout;
		while ((!isAdLoaded()) && (now < timeoutTime)) {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
			}
			now = System.currentTimeMillis();
		}
		mListener = l;
		showAd();
	}

	public void showAd() {
		Activity activity = (Activity) getActivity();

		if ((mResponse == null)
				|| (mResponse.getType() == Const.NO_AD)
				|| (mResponse.getType() == Const.AD_FAILED)) {
			notifyAdShown(mResponse, false);
			return;
		}
		RichMediaAd ad = mResponse;
		boolean result = false;
		try {
			if (Util.isNetworkAvailable(getActivity())) {
				ad.setTimestamp(System.currentTimeMillis());
				Intent intent = new Intent(activity,
						RichMediaActivity.class);
				intent.putExtra(AD_EXTRA, ad);
				activity.startActivityForResult(intent, 0);
				int enterAnim = Util.getEnterAnimation(ad.getAnimation());
				int exitAnim = Util.getExitAnimation(ad.getAnimation());
				RichMediaActivity.setActivityAnimation(activity,
						enterAnim, exitAnim);
				result = true;
				sRunningAds.put(ad.getTimestamp(), this);
			}
		} catch (Exception e) {
		} finally {
			notifyAdShown(ad, result);
		}
	}

	private void initialize() throws IllegalArgumentException {
		mUserAgent = Util.getDefaultUserAgentString(getActivity());
		this.mUniqueId1 = Util.getTelephonyDeviceId(getActivity());
		this.mUniqueId2 = Util.getDeviceId(getActivity());
		if ((mPublisherId == null) || (mPublisherId.length() == 0)) {
			throw new IllegalArgumentException(
					"User Id cannot be null or empty");
		}
		if ((mUniqueId2 == null) || (mUniqueId2.length() == 0)) {
			throw new IllegalArgumentException(
					"System Device Id cannot be null or empty");
		}
		mEnabled = (Util.getMemoryClass(getActivity()) > 16);
		Util.initializeAnimations(getActivity());

	}

	private void notifyNoAdFound() {
		if (mListener != null) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					mListener.noAdFound();
				}
			});
		}
		this.mResponse = null;
	}

	private void notifyAdShown(final RichMediaAd ad, final boolean ok) {
		if (mListener != null) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					mListener.adShown(ad, ok);
				}
			});
		}
		this.mResponse = null;
	}

	private void notifyAdClose(final RichMediaAd ad, final boolean ok) {
		if (mListener != null) {
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					mListener.adClosed(ad, ok);
				}
			});
		}
	}

	private AdRequest getRequest() {
		if (mRequest == null) {
			mRequest = new AdRequest();
			mRequest.setDeviceId(mUniqueId1);
			mRequest.setDeviceId2(mUniqueId2);
			mRequest.setPublisherId(mPublisherId);
			mRequest.setUserAgent(mUserAgent);
			mRequest.setUserAgent2(Util.buildUserAgent());
		}
		Location location = null;
		if (this.mIncludeLocation) {
			location = Util.getLocation(getActivity());
		}
		if (location != null) {
			mRequest.setLatitude(location.getLatitude());
			mRequest.setLongitude(location.getLongitude());
		} else {
			mRequest.setLatitude(0.0);
			mRequest.setLongitude(0.0);
		}
		mRequest.setConnectionType(Util.getConnectionType(getActivity()));
		mRequest.setIpAddress(Util.getLocalIpAddress());
		mRequest.setTimestamp(System.currentTimeMillis());

		mRequest.setType(AdRequest.VAD);
		mRequest.setRequestURL(this.requestURL);
		return mRequest;
	}

	private Context getActivity() {
		return getmActivity();
	}

	private static Context getmActivity() {
		return mActivity;
	}

	private static void setmActivity(Activity mActivity) {
		AdManager.mActivity = mActivity;
	}

}
