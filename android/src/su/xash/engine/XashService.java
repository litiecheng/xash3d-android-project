package su.xash.engine;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.egl.*;

import android.app.*;
import android.content.*;
import android.view.*;
import android.os.*;
import android.util.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.method.*;
import android.text.*;
import android.media.*;
import android.hardware.*;
import android.content.*;
import android.widget.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.*;
import android.database.*;

import android.view.inputmethod.*;

import java.lang.*;
import java.util.List;
import java.security.MessageDigest;

import su.xash.engine.R;
import su.xash.engine.XashConfig;
import su.xash.engine.JoystickHandler;


public class XashService extends Service 
{
	public static XashNotification not;

	@Override
	public IBinder onBind(Intent intent) 
	{
		return null;
	}
	
	public static class ExitButtonListener extends BroadcastReceiver 
	{
		@Override
		public void onReceive(Context context, Intent intent) 
		{
			XashActivity.mEngineReady = false;
			XashActivity.nativeUnPause();
			XashActivity.nativeOnDestroy();
			if( XashActivity.mSurface != null )
				XashActivity.mSurface.engineThreadJoin();
			System.exit(0);
		}
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) 
	{
		Log.d("XashService", "Service Started");
		
		not = XashNotification.getXashNotification(this);
		
		startForeground(not.getId(), not.createNotification());
		
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() 
	{
		super.onDestroy();
		Log.d("XashService", "Service Destroyed");
	}

	@Override
	public void onCreate()
	{
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) 
	{
		Log.e("XashService", "OnTaskRemoved");
		//if( XashActivity.mEngineReady )
		{
			XashActivity.mEngineReady = false;
			XashActivity.nativeUnPause();
			XashActivity.nativeOnDestroy();
			if( XashActivity.mSurface != null )
				XashActivity.mSurface.engineThreadJoin();
			System.exit(0);
		}
		stopSelf();
	}
	
	public static class XashNotification
	{
		public Notification notification;
		public final int notificationId = 100;
		public Context ctx;
		
		public XashNotification(Context ctx)
		{
			this.ctx = ctx;
		}
	
		public Notification createNotification()
		{
			Intent engineIntent = new Intent(ctx, XashActivity.class);
			engineIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

			Intent exitIntent = new Intent(ctx, ExitButtonListener.class);
			final PendingIntent pendingExitIntent = PendingIntent.getBroadcast(ctx, 0, exitIntent, 0);

			notification = new Notification(R.drawable.ic_statusbar, ctx.getString(R.string.app_name), System.currentTimeMillis());
			
			notification.contentView = new RemoteViews(ctx.getApplicationContext().getPackageName(), R.layout.notify);
			notification.contentView.setTextViewText(R.id.status_text, ctx.getString(R.string.app_name));
			notification.contentView.setOnClickPendingIntent(R.id.status_exit_button, pendingExitIntent);

			notification.contentIntent = PendingIntent.getActivity(ctx.getApplicationContext(), 0, engineIntent, 0);
			notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE;
			
			return notification;
		}
		
		public void setIcon(Bitmap bmp)
		{
			notification.contentView.setImageViewBitmap( R.id.status_image, bmp );
			NotificationManager nm = ctx.getSystemService(NotificationManager.class);
			nm.notify( notificationId, notification );
		}
		
		public void setText(String title)
		{
			notification.contentView.setTextViewText( R.id.status_text, title );
			NotificationManager nm = ctx.getSystemService(NotificationManager.class);
			nm.notify( notificationId, notification );
		}
		
		public int getId()
		{
			return notificationId;
		}
		
		public static XashNotification getXashNotification(Context ctx)
		{
			if( XashActivity.sdk >= Build.VERSION_CODES.O )
				return new XashNotification_O(ctx);
			else if( XashActivity.sdk >= 21 )
				return new XashNotification_v21(ctx);
			return new XashNotification(ctx);
		}
	}
	
	private static class XashNotification_v21 extends XashNotification
	{
		protected Notification.Builder builder;
		
		public XashNotification_v21(Context ctx)
		{
			super(ctx);
		}
		
		@Override
		public Notification createNotification()
		{
			Intent engineIntent = new Intent(ctx, XashActivity.class);
			engineIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

			Intent exitIntent = new Intent(ctx, ExitButtonListener.class);
			final PendingIntent pendingExitIntent = PendingIntent.getBroadcast(ctx, 0, exitIntent, 0);

			if(builder == null)
				builder = new Notification.Builder(ctx);
			
			notification = builder.setSmallIcon(R.drawable.ic_statusbar)
				.setLargeIcon(Icon.createWithResource(ctx, R.mipmap.ic_launcher))
				.setContentTitle(ctx.getString(R.string.app_name))
				.setContentText(ctx.getString(R.string.app_name))
				.setContentIntent(PendingIntent.getActivity(ctx.getApplicationContext(), 0, engineIntent, 0))
				.addAction(new Notification.Action.Builder(R.drawable.empty, ctx.getString(R.string.exit), pendingExitIntent).build())
				.setOngoing(true)
				.build();
			
			return notification;
		}
		
		@Override
		public void setIcon(Bitmap bmp)
		{
			notification = builder.setLargeIcon(bmp).build();		
			NotificationManager nm = ctx.getSystemService(NotificationManager.class);
			nm.notify( notificationId, notification );
		}
		
		@Override
		public void setText(String str)
		{
			notification = builder.setContentText(str).build();
			NotificationManager nm = ctx.getSystemService(NotificationManager.class);
			nm.notify( notificationId, notification );
		}
	}
	
	private static class XashNotification_O extends XashNotification_v21
	{
		private static final String CHANNEL_ID = "XashServiceChannel";
		
		public XashNotification_O(Context ctx)
		{
			super(ctx);
		}
	
		private void createNotificationChannel()
		{
			// Create the NotificationChannel, but only on API 26+ because
			// the NotificationChannel class is new and not in the support library
			if (XashActivity.sdk >= Build.VERSION_CODES.O)
			{
				final NotificationManager nm = ctx.getSystemService(NotificationManager.class);
				
				if(nm.getNotificationChannel(CHANNEL_ID) == null)
				{
					CharSequence name = ctx.getString(R.string.default_channel_name);
					String description = ctx.getString(R.string.default_channel_description);
					int importance = NotificationManager.IMPORTANCE_LOW;
				 
					NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
					channel.setDescription(description);
			
					// Register the channel with the system; you can't change the importance
					// or other notification behaviors after this
				
					nm.createNotificationChannel(channel);
				}
			}
		}
		
		@Override
		public Notification createNotification()
		{
			createNotificationChannel();
		
			builder = new Notification.Builder(ctx);
			builder.setChannelId(CHANNEL_ID);
			
			return super.createNotification();
		}
	}
};
