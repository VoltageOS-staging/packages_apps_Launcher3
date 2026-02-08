package com.android.launcher3.popup;

import android.animation.ValueAnimator;
import android.app.WallpaperManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.views.IconFrame;
import com.android.launcher3.R;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.wallpaper.Wallpaper;
import com.android.launcher3.wallpaper.WallpaperDatabase;

import java.io.File;
import java.security.MessageDigest;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.List;

public class WallpaperCarouselView extends LinearLayout {
    private final DeviceProfile deviceProfile;
    private static final String TAG = "WallpaperCarouselView";
    private final ProgressBar loadingView;
    private int currentItemIndex = 0;
    private final IconFrame iconFrame;
    private Wallpaper currentWallpaper;
    private final Set<String> wallpaperHashes = new HashSet<>();

    public WallpaperCarouselView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        deviceProfile = ActivityContext.lookupContext(context).getDeviceProfile();
        iconFrame = new IconFrame(context);
        iconFrame.setIcon(R.drawable.ic_tick);
        loadingView = new ProgressBar(context);
        loadingView.setIndeterminate(true);
        loadingView.setVisibility(VISIBLE);
        addView(loadingView);
        fetchWallpapers();
    }

    private void fetchWallpapers() {
        if (!LauncherPrefs.WALLPAPER_CAROUSEL.get(getContext())) {
            MAIN_EXECUTOR.execute(() -> {
                loadingView.setVisibility(GONE);
                setVisibility(GONE);
                ViewGroup parent = (ViewGroup) getParent();
                if (parent != null) {
                    parent.requestLayout();
                }
            });
            return;
        }

        UI_HELPER_EXECUTOR.execute(() -> {
            try {
                wallpaperHashes.clear();
                
                List<Wallpaper> wallpapers = WallpaperDatabase.INSTANCE.get(getContext()).getTopWallpapers();

                List<Wallpaper> partnerWallpapers = queryPartnerWallpapers();
                wallpapers.addAll(partnerWallpapers);

                List<Wallpaper> uniqueWallpapers = new ArrayList<>();

                if (currentWallpaper != null) {
                    String hash = calculateWallpaperHash(currentWallpaper);
                    if (hash != null) {
                        wallpaperHashes.add(hash);
                        uniqueWallpapers.add(currentWallpaper);
                    }
                }

                for (Wallpaper wallpaper : wallpapers) {
                    String hash = calculateWallpaperHash(wallpaper);
                    if (hash != null && !wallpaperHashes.contains(hash)) {
                        wallpaperHashes.add(hash);
                        uniqueWallpapers.add(wallpaper);
                    }
                }

                MAIN_EXECUTOR.execute(() -> {
                    loadingView.setVisibility(GONE);
                    if (uniqueWallpapers.isEmpty()) {
                        setVisibility(GONE);
                        ViewGroup parent = (ViewGroup) getParent();
                        if (parent != null) {
                            parent.requestLayout();
                        }
                    } else {
                        setVisibility(VISIBLE);
                        displayWallpapers(uniqueWallpapers);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error fetching wallpapers: " + e.getMessage());
                MAIN_EXECUTOR.execute(() -> {
                    loadingView.setVisibility(GONE);
                    setVisibility(GONE);
                    ViewGroup parent = (ViewGroup) getParent();
                    if (parent != null) {
                        parent.requestLayout();
                    }
                });
            }
        });
    }

    private void displayWallpapers(List<Wallpaper> wallpapers) {
        if (!LauncherPrefs.WALLPAPER_CAROUSEL.get(getContext())) {
            setVisibility(GONE);
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) {
                parent.requestLayout();
            }
            return;
        }

        if (getChildAt(0) instanceof ProgressBar) {
            removeViewAt(0);
        }

        if (isWallpaperListChanged(wallpapers)) {
            removeAllViews();

            int totalWidth = getWidth() > 0 ? getWidth() : (int) (deviceProfile.getDeviceProperties().getWidthPx() * 0.8);
            double firstItemWidth = totalWidth * 0.45;
            double remainingWidth = totalWidth - firstItemWidth;
            double marginBetweenItems = totalWidth * 0.02;
            double itemWidth = (remainingWidth - (marginBetweenItems * (wallpapers.size() - 1))) / (wallpapers.size() - 1);

            for (int index = 0; index < wallpapers.size(); index++) {
                Wallpaper wallpaper = wallpapers.get(index);
                if (isWallpaperInvalid(wallpaper)) continue;
                CardView cardView = createWallpaperCard(wallpaper, index, firstItemWidth, itemWidth, marginBetweenItems);
                addView(cardView);
                loadWallpaperBitmapAsync(wallpaper, cardView);
            }
        }
    }

    private boolean isWallpaperListChanged(List<Wallpaper> wallpapers) {
        int cardViewIndex = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);

            if (!(child instanceof CardView)) {
                continue;
            }

            CardView existingCard = (CardView) child;
            Wallpaper existingWallpaper = (Wallpaper) existingCard.getTag();

            if (cardViewIndex >= wallpapers.size() || existingWallpaper == null) {
                return true;
            }

            Wallpaper newWallpaper = wallpapers.get(cardViewIndex);
            if (!existingWallpaper.equals(newWallpaper)) {
                return true;
            }

            cardViewIndex++;
        }

        return cardViewIndex != wallpapers.size();
    }

    private boolean isWallpaperInvalid(Wallpaper wallpaper) {
        return wallpaper == null || wallpaper.getImagePath() == null || wallpaper.getImagePath().isEmpty();
    }

    private CardView createWallpaperCard(Wallpaper wallpaper, int index, double firstItemWidth, double itemWidth, double marginBetweenItems) {
        CardView cardView = new CardView(getContext());
        cardView.setRadius(Themes.getDialogCornerRadius(getContext()) / 2);
        cardView.setCardElevation(0);

        LayoutParams layoutParams = new LayoutParams(
                index == currentItemIndex ? (int) firstItemWidth : (int) itemWidth,
                LayoutParams.MATCH_PARENT
        );
        layoutParams.setMargins(index > 0 ? (int) marginBetweenItems : 0, 0, 0, 0);
        cardView.setLayoutParams(layoutParams);

        cardView.setTag(wallpaper);

        cardView.setOnClickListener(v -> {
            if (index != currentItemIndex) {
                animateWidthTransition(index, firstItemWidth, itemWidth);
            }
            setWallpaper(wallpaper);
        });

        ImageView placeholder = new ImageView(getContext());
        placeholder.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_deepshortcut_placeholder));
        placeholder.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cardView.addView(placeholder);

        return cardView;
    }

    private void loadWallpaperBitmapAsync(Wallpaper wallpaper, CardView cardView) {
        UI_HELPER_EXECUTOR.execute(() -> {
            try {
                String imagePath = wallpaper.getImagePath();
                
                if (imagePath.startsWith("partner://")) {
                    Drawable drawable = loadPartnerWallpaperDrawable(imagePath);
                    if (drawable != null) {
                        post(() -> {
                            ImageView imageView = (ImageView) cardView.getChildAt(0);
                            if (imageView != null) imageView.setImageDrawable(drawable);
                            if (cardView == getChildAt(currentItemIndex)) addIconFrameToCard(cardView);
                        });
                    }
                    return;
                }
                
                File imageFile = new File(imagePath);
                if (imageFile.exists() && imageFile.canRead()) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 2;
                    Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
                    if (bitmap != null) {
                        post(() -> {
                            ImageView imageView = (ImageView) cardView.getChildAt(0);
                            if (imageView != null) imageView.setImageBitmap(bitmap);
                            if (cardView == getChildAt(currentItemIndex)) addIconFrameToCard(cardView);
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading wallpaper bitmap: " + e.getMessage());
            }
        });
    }

    private void setWallpaper(Wallpaper wallpaper) {
        if (wallpaper.equals(currentWallpaper)) {
            fetchWallpapers();
            return;
        }

        ProgressBar loadingSpinner = new ProgressBar(getContext());
        loadingSpinner.setIndeterminate(true);
        FrameLayout.LayoutParams spinnerParams = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        spinnerParams.gravity = Gravity.CENTER;
        loadingSpinner.setLayoutParams(spinnerParams);

        CardView currentCardView = (CardView) getChildAt(currentItemIndex);
        currentCardView.removeView(iconFrame);
        currentCardView.addView(loadingSpinner);

        UI_HELPER_EXECUTOR.execute(() -> {
            try {
                WallpaperManager wallpaperManager = WallpaperManager.getInstance(getContext());
                
                if (wallpaper.getImagePath().startsWith("partner://")) {
                    Drawable drawable = loadPartnerWallpaperDrawable(wallpaper.getImagePath());
                    if (drawable != null) {
                        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), 
                                                           drawable.getIntrinsicHeight(), 
                                                           Bitmap.Config.ARGB_8888);
                        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                        drawable.draw(canvas);
                        
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM);
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK);
                        bitmap.recycle();
                        
                        currentWallpaper = wallpaper;
                        
                        MAIN_EXECUTOR.execute(() -> {
                            currentCardView.removeView(loadingSpinner);
                            addIconFrameToCard(currentCardView);
                        });
                    }
                    return;
                }
                
                Bitmap bitmap = BitmapFactory.decodeFile(wallpaper.getImagePath());
                if (bitmap != null) {
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM);
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK);

                    wallpaper.setTimestamp(System.currentTimeMillis());
                    WallpaperDatabase.INSTANCE.get(getContext()).insertOrUpdate(wallpaper);

                    currentWallpaper = wallpaper;

                    fetchWallpapers();

                    MAIN_EXECUTOR.execute(() -> {
                        currentCardView.removeView(loadingSpinner);
                        addIconFrameToCard(currentCardView);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting wallpaper: " + e.getMessage());
                MAIN_EXECUTOR.execute(() -> {
                    currentCardView.removeView(loadingSpinner);
                    addIconFrameToCard(currentCardView);
                });
            }
        });
    }

    private void addIconFrameToCard(CardView cardView) {
        if (iconFrame.getParent() != null) {
            ((ViewGroup) iconFrame.getParent()).removeView(iconFrame);
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        iconFrame.setBackgroundWithRadius(Themes.getColorAccent(getContext()), 100F);
        cardView.addView(iconFrame, params);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (!LauncherPrefs.WALLPAPER_CAROUSEL.get(getContext()) || getVisibility() == GONE || getChildCount() == 0) {
            setMeasuredDimension(0, 0);
            return;
        }
        
        int adjustedWidth = (int) (deviceProfile.getDeviceProperties().getWidthPx() * 0.8);
        int width = MeasureSpec.makeMeasureSpec(adjustedWidth, MeasureSpec.EXACTLY);
        super.onMeasure(width, heightMeasureSpec);
    }

    private void animateWidthTransition(int newIndex, double firstItemWidth, double itemWidth) {
        currentItemIndex = newIndex;
        for (int i = 0; i < getChildCount(); i++) {
            CardView cardView = (CardView) getChildAt(i);
            int targetWidth = (i == currentItemIndex) ? (int) firstItemWidth : (int) itemWidth;
            LayoutParams layoutParams = (LayoutParams) cardView.getLayoutParams();
            if (layoutParams.width != targetWidth) {
                ValueAnimator animator = ValueAnimator.ofInt(layoutParams.width, targetWidth);
                animator.setDuration(300L);
                animator.addUpdateListener(animation -> {
                    layoutParams.width = (int) animation.getAnimatedValue();
                    cardView.setLayoutParams(layoutParams);
                });
                animator.start();
            }
            if (i == currentItemIndex) addIconFrameToCard(cardView);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        UI_HELPER_EXECUTOR.execute(() -> {
            currentWallpaper = null;
            wallpaperHashes.clear();

            MAIN_EXECUTOR.execute(() -> {
                iconFrame.setImageBitmap(null);
                removeAllViews();
            });
        });
    }

    /**
     * Query partner wallpaper providers like Covers app
     * Partner apps register with action "com.android.launcher3.action.PARTNER_CUSTOMIZATION"
     */
    private List<Wallpaper> queryPartnerWallpapers() {
        List<Wallpaper> wallpapers = new ArrayList<>();
        PackageManager pm = getContext().getPackageManager();
        
        Intent intent = new Intent("com.android.launcher3.action.PARTNER_CUSTOMIZATION");
        List<ResolveInfo> providers = pm.queryBroadcastReceivers(intent, 0);
        
        for (ResolveInfo provider : providers) {
            try {
                String packageName = provider.activityInfo.packageName;
                Log.d(TAG, "Found partner wallpaper provider: " + packageName);
                
                Resources partnerRes = pm.getResourcesForApplication(packageName);
                
                int arrayId = partnerRes.getIdentifier("partner_wallpapers", "array", packageName);
                if (arrayId == 0) {
                    Log.w(TAG, "No partner_wallpapers array found in " + packageName);
                    continue;
                }
                
                String[] wallpaperNames = partnerRes.getStringArray(arrayId);
                Log.d(TAG, "Found " + wallpaperNames.length + " wallpapers in " + packageName);
                
                for (int i = 0; i < wallpaperNames.length; i++) {
                    String wallpaperName = wallpaperNames[i];
                    
                    String imagePath = "partner://" + packageName + "/" + wallpaperName;
                    
                    long timestamp = System.currentTimeMillis() - (i * 1000);
                    
                    Wallpaper wallpaper = new Wallpaper(0, imagePath, i, timestamp);
                    wallpapers.add(wallpaper);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading partner wallpapers from " + provider.activityInfo.packageName, e);
            }
        }
        
        Log.d(TAG, "Loaded " + wallpapers.size() + " partner wallpapers");
        return wallpapers;
    }

    /**
     * Load a partner wallpaper drawable from the provider app
     */
    private Drawable loadPartnerWallpaperDrawable(String partnerPath) {
        try {
            String[] parts = partnerPath.replace("partner://", "").split("/", 2);
            if (parts.length != 2) {
                Log.e(TAG, "Invalid partner path format: " + partnerPath);
                return null;
            }
            
            String packageName = parts[0];
            String resourceName = parts[1];
            
            Resources partnerRes = getContext().getPackageManager().getResourcesForApplication(packageName);
            int drawableId = partnerRes.getIdentifier(resourceName, "drawable", packageName);
            
            if (drawableId == 0) {
                Log.e(TAG, "Could not find drawable: " + resourceName);
                return null;
            }
            
            return partnerRes.getDrawable(drawableId, null);
        } catch (Exception e) {
            Log.e(TAG, "Error loading partner wallpaper drawable: " + partnerPath, e);
            return null;
        }
    }

    /**
     * Calculate a hash for the wallpaper to detect duplicates based on image content
     * Returns null if the wallpaper cannot be loaded or hashed
     */
    private String calculateWallpaperHash(Wallpaper wallpaper) {
        if (wallpaper == null || wallpaper.getImagePath() == null) {
            return null;
        }

        try {
            Bitmap bitmap = null;
            String imagePath = wallpaper.getImagePath();

            if (imagePath.startsWith("partner://")) {
                Drawable drawable = loadPartnerWallpaperDrawable(imagePath);
                if (drawable instanceof BitmapDrawable) {
                    bitmap = ((BitmapDrawable) drawable).getBitmap();
                } else if (drawable != null) {
                    bitmap = Bitmap.createBitmap(
                        drawable.getIntrinsicWidth(),
                        drawable.getIntrinsicHeight(),
                        Bitmap.Config.ARGB_8888
                    );
                    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    drawable.draw(canvas);
                }
            } else {
                File imageFile = new File(imagePath);
                if (imageFile.exists() && imageFile.canRead()) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 8;
                    bitmap = BitmapFactory.decodeFile(imagePath, options);
                }
            }

            if (bitmap == null) {
                return null;
            }

            int hashWidth = Math.min(bitmap.getWidth(), 32);
            int hashHeight = Math.min(bitmap.getHeight(), 32);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, hashWidth, hashHeight, false);

            int[] pixels = new int[hashWidth * hashHeight];
            scaledBitmap.getPixels(pixels, 0, hashWidth, 0, 0, hashWidth, hashHeight);

            MessageDigest md = MessageDigest.getInstance("MD5");
            ByteBuffer buffer = ByteBuffer.allocate(pixels.length * 4);
            for (int pixel : pixels) {
                buffer.putInt(pixel);
            }
            byte[] digest = md.digest(buffer.array());

            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }

            scaledBitmap.recycle();
            if (!imagePath.startsWith("partner://")) {
                bitmap.recycle();
            }

            return hexString.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error calculating wallpaper hash for: " + wallpaper.getImagePath(), e);
            return null;
        }
    }
}
