package huawei.com.android.internal.widget;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentManager.BackStackEntry;
import android.app.FragmentTransaction;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class HwFragmentContainer {
    protected static final int ANIMATION_DURATION = 300;
    private static final int ANIMATION_TYPE_LEFT_IN = 2;
    private static final int ANIMATION_TYPE_LEFT_OUT = 1;
    private static final int ANIMATION_TYPE_RIGHT_IN = 0;
    private static final int ANIMATION_TYPE_RIGHT_OUT = 3;
    protected static final float BLUR_LAYER_FORTY_PERCENT = 0.4f;
    protected static final float BLUR_LAYER_TRANSPARENT = 0.0f;
    public static final int COLUMN_NUMBER_ONE = 1;
    public static final int COLUMN_NUMBER_TWO = 2;
    public static final int CONTAINER_BOTH = 2;
    public static final int CONTAINER_LEFT = 0;
    public static final int CONTAINER_RIGHT = 1;
    private static final boolean DEBUG = false;
    public static final float DISPLAY_RATE_FIFTY_PERCENT = 0.5f;
    public static final float DISPLAY_RATE_FORTY_PERCENT = 0.4f;
    public static final float DISPLAY_RATE_SIXTY_PERCENT = 0.6f;
    protected static final int FRAGMENT_BACKGROUND_COLORCOLOR = -197380;
    private static final int FRAGMENT_LAYOUT_ID = 655360;
    public static final int FRAGMENT_LEVEL_1 = 1;
    public static final int FRAGMENT_LEVEL_2 = 2;
    public static final int FRAGMENT_LEVEL_3 = 3;
    public static final int FRAGMENT_LEVEL_NONE = 0;
    protected static final PathInterpolator INTERPOLATOR_20_90 = new PathInterpolator(HwFragmentMenuItemView.ALPHA_DISABLE, 0.15f, 0.1f, 0.85f);
    protected static final PathInterpolator INTERPOLATOR_33_33 = new PathInterpolator(0.2f, 0.5f, HwRippleForegroundImpl.LINEAR_FROM, 0.5f);
    public static final int SPLITE_MODE_ALL_SEPARATE = 3;
    public static final int SPLITE_MODE_DEFAULT_SEPARATE = 0;
    public static final int SPLITE_MODE_LAND_SEPARATE = 2;
    public static final int SPLITE_MODE_NONE_SEPARATE = 1;
    private static final String TAG = "FragmentContainer";
    private static final String TAG_LEFT_CONTAINER = "left_container";
    private static final String TAG_RIGHT_CONTAINER = "right_container";
    public static final int TRANSITION_FADE = 1;
    public static final int TRANSITION_SLIDE_HORIZONTAL = 2;
    private int mAnimatorWidth;
    private ObjectAnimator mBlurLayerAnimator;
    private HwFragmentLayout mFragmentLayout;
    private FragmentManager mFragmentManager;
    private boolean mNoFragmentAnim;
    private int mPopCount;
    private boolean mPopMultiple;

    private static class TranslateAnimatorListener implements AnimatorListener {
        boolean hasSetBackground;
        private View mView;

        private TranslateAnimatorListener() {
            this.hasSetBackground = false;
            this.mView = null;
        }

        protected void setView(View view) {
            this.mView = view;
        }

        public void onAnimationStart(Animator animation) {
            if (this.mView != null && this.mView.getBackground() == null) {
                this.hasSetBackground = true;
                this.mView.setBackgroundColor(HwFragmentContainer.FRAGMENT_BACKGROUND_COLORCOLOR);
            }
        }

        public void onAnimationEnd(Animator animation) {
            if (this.mView != null && this.hasSetBackground) {
                this.mView.setBackground(null);
                this.mView = null;
            }
        }

        public void onAnimationCancel(Animator animation) {
            if (this.mView != null && this.hasSetBackground) {
                this.mView.setBackground(null);
                this.mView = null;
            }
        }

        public void onAnimationRepeat(Animator animation) {
        }
    }

    public HwFragmentContainer(Context context, FragmentManager fm) {
        this(context, 0.4f, fm);
    }

    public HwFragmentContainer(Context context, float displayRate, FragmentManager fm) {
        this(context, displayRate, false, fm);
    }

    public HwFragmentContainer(Context context, float displayRate, boolean canMove, FragmentManager fm) {
        this.mAnimatorWidth = 0;
        this.mPopMultiple = false;
        this.mPopCount = 0;
        this.mNoFragmentAnim = false;
        this.mFragmentLayout = new HwFragmentLayout(context, displayRate, canMove);
        if (this.mFragmentLayout.getId() == -1) {
            this.mFragmentLayout.setId(FRAGMENT_LAYOUT_ID);
        }
        this.mFragmentManager = fm;
    }

    public HwFragmentContainer(Context context, HwFragmentLayout fragmentLayout, FragmentManager fm) {
        this.mAnimatorWidth = 0;
        this.mPopMultiple = false;
        this.mPopCount = 0;
        this.mNoFragmentAnim = false;
        this.mFragmentLayout = fragmentLayout;
        this.mFragmentManager = fm;
    }

    public View getFragmentLayout() {
        return this.mFragmentLayout.getFragmentLayout();
    }

    public FrameLayout getLeftLayout() {
        return this.mFragmentLayout.getLeftLayout();
    }

    public FrameLayout getRightLayout() {
        return this.mFragmentLayout.getRightLayout();
    }

    public ImageView getSplitLine() {
        return this.mFragmentLayout.getSplitLine();
    }

    public ImageView getSplitBtn() {
        return this.mFragmentLayout.getSplitBtn();
    }

    private int getLeftContentID() {
        return this.mFragmentLayout.getLeftContentID();
    }

    private int getRightContentID() {
        return this.mFragmentLayout.getRightContentID();
    }

    public ImageView getLeftBlurLayer() {
        return this.mFragmentLayout.getLeftBlurLayer();
    }

    public ImageView getRightBlurLayer() {
        return this.mFragmentLayout.getRightBlurLayer();
    }

    public void setDisplayRate(float displayRate) {
        this.mFragmentLayout.setDisplayRate(displayRate);
    }

    public void setCanMove(boolean canMove) {
        this.mFragmentLayout.setCanMove(canMove);
    }

    public void setSplitMode(int splitMode) {
        this.mFragmentLayout.setSplitMode(splitMode);
    }

    public int getColumnsNumber() {
        return this.mFragmentLayout.calculateColumnsNumber();
    }

    public int getColumnsNumber(int orientation, int appWidth) {
        return this.mFragmentLayout.calculateColumnsNumber(orientation, appWidth);
    }

    public void setSeparateDeviceSize(double landSeparateSize, double portSeparteSize) {
        this.mFragmentLayout.setSeparateDeviceSize(landSeparateSize, portSeparteSize);
    }

    public void openLeftClearStack(Fragment fragment) {
        if (fragment != null) {
            setSelectedAndAnimation(0);
            popPrepare();
            this.mFragmentManager.popBackStackImmediate(TAG_LEFT_CONTAINER, 1);
            popFinish();
            FragmentTransaction transaction = this.mFragmentManager.beginTransaction();
            transaction.setTransition(4097);
            transaction.replace(getLeftContentID(), fragment, fragment.getClass().getName());
            transaction.addToBackStack(TAG_LEFT_CONTAINER);
            transaction.commitAllowingStateLoss();
            refreshFragmentLayout();
        }
    }

    public void openRightClearStack(Fragment fragment) {
        openRightClearStack(fragment, false);
    }

    public void initRightContainer(Fragment fragment) {
        openRightClearStack(fragment, true);
    }

    private void openRightClearStack(Fragment fragment, boolean isInitial) {
        if (fragment != null) {
            setSelectedAndAnimation(isInitial ? 0 : 1);
            popPrepare();
            this.mFragmentManager.popBackStackImmediate(TAG_LEFT_CONTAINER, 0);
            popFinish();
            FragmentTransaction transaction = this.mFragmentManager.beginTransaction();
            transaction.setTransition(4097);
            transaction.replace(getRightContentID(), fragment, fragment.getClass().getName());
            transaction.addToBackStack(TAG_RIGHT_CONTAINER);
            transaction.commitAllowingStateLoss();
            this.mFragmentManager.executePendingTransactions();
            if (!isInitial) {
                refreshFragmentLayout();
            }
        }
    }

    public void changeRightAddToStack(Fragment nextFragment, Fragment currentFragment) {
        if (nextFragment != null) {
            setSelectedAndAnimation(1);
            FragmentTransaction transaction = this.mFragmentManager.beginTransaction();
            transaction.setTransition(4097);
            if (currentFragment == null) {
                transaction.replace(getRightContentID(), nextFragment, nextFragment.getClass().getName());
            } else if (nextFragment.isAdded()) {
                transaction.hide(currentFragment);
                transaction.show(nextFragment);
            } else {
                transaction.hide(currentFragment);
                transaction.add(getRightContentID(), nextFragment, nextFragment.getClass().getName());
            }
            transaction.addToBackStack(TAG_RIGHT_CONTAINER);
            transaction.commitAllowingStateLoss();
            this.mFragmentManager.executePendingTransactions();
            refreshFragmentLayout();
        }
    }

    public void setSelectedContainer(int selectedContainer) {
        this.mFragmentLayout.setSelectedContainer(selectedContainer);
    }

    private void setSelectedAndAnimation(int selectedContainer) {
        if (1 != getColumnsNumber() || selectedContainer == getSelectedContainer()) {
            this.mNoFragmentAnim = false;
        } else {
            this.mNoFragmentAnim = true;
        }
        setSelectedContainer(selectedContainer);
    }

    public int getSelectedContainer() {
        return this.mFragmentLayout.getSelectedContainer();
    }

    public void refreshFragmentLayout() {
        this.mFragmentLayout.refreshFragmentLayout();
    }

    public int[] getLeftRightBackStackCount() {
        int[] stackCounts = new int[2];
        int leftStackCount = 0;
        int rightStackCount = 0;
        int backStackCount = this.mFragmentManager.getBackStackEntryCount();
        for (int i = 0; i < backStackCount; i++) {
            BackStackEntry backstatck = this.mFragmentManager.getBackStackEntryAt(i);
            if (backstatck.getName() != null && backstatck.getName().indexOf(TAG_LEFT_CONTAINER) >= 0) {
                leftStackCount++;
            } else if (backstatck.getName() != null && backstatck.getName().indexOf(TAG_RIGHT_CONTAINER) >= 0) {
                rightStackCount++;
            }
        }
        stackCounts[0] = leftStackCount;
        stackCounts[1] = rightStackCount;
        return stackCounts;
    }

    public boolean isBackPressed() {
        int rightStackCount = getLeftRightBackStackCount()[1];
        int columnNumber = getColumnsNumber();
        if (rightStackCount > 1) {
            this.mFragmentManager.popBackStackImmediate();
            this.mFragmentLayout.setSelectedContainer(1);
            return false;
        }
        if (columnNumber != 1) {
            return (columnNumber == 2 && rightStackCount == 1) ? true : true;
        } else {
            if (rightStackCount != 1 || getRightLayout().getVisibility() != 0) {
                return true;
            }
            this.mFragmentLayout.setSelectedContainer(0);
            refreshFragmentLayout();
            return false;
        }
    }

    public void setSelectContainerByTouch(boolean enabled) {
        this.mFragmentLayout.setSelectContainerByTouch(enabled);
    }

    public void logBackStacksInfo() {
        int leftStackCount = 0;
        int rightStackCount = 0;
        int rightOpenStackCount = 0;
        int backStackCount = this.mFragmentManager.getBackStackEntryCount();
        for (int i = 0; i < backStackCount; i++) {
            BackStackEntry backstatck = this.mFragmentManager.getBackStackEntryAt(i);
            if (backstatck.getName() != null && backstatck.getName().indexOf(TAG_LEFT_CONTAINER) >= 0) {
                leftStackCount++;
            } else if (backstatck.getName() != null && backstatck.getName().indexOf(TAG_RIGHT_CONTAINER) >= 0) {
                rightStackCount++;
            }
            if (TAG_RIGHT_CONTAINER.equals(backstatck.getName())) {
                rightOpenStackCount++;
            }
            Log.d(TAG, "ID = " + backstatck.getId() + ", name = " + backstatck.getName());
        }
        Log.d(TAG, "BackStackCount = " + backStackCount + ", leftStackCount = " + leftStackCount + ", rightStackCount = " + rightStackCount + ", rightOpenStackCount = " + rightOpenStackCount);
    }

    private void popPrepare() {
        this.mPopMultiple = true;
        this.mPopCount = 0;
    }

    private void popFinish() {
        this.mPopMultiple = false;
        this.mPopCount = 0;
    }

    public Animator getAnimator(View view, int transit, boolean enter) {
        this.mFragmentLayout.displayAnimation();
        if (view == null || this.mNoFragmentAnim || getSelectedContainer() != 1) {
            return null;
        }
        int width = view.getWidth();
        if (width == 0) {
            width = this.mAnimatorWidth;
        }
        this.mAnimatorWidth = width;
        if (this.mAnimatorWidth == 0) {
            return null;
        }
        AnimatorSet animatorSet = null;
        if (transit == 4097) {
            if (enter) {
                animatorSet = createAnimator(view, 0);
            } else {
                animatorSet = createAnimator(view, 1);
            }
        } else if (transit == 8194) {
            if (this.mPopMultiple) {
                if (view.getVisibility() == 0 && this.mPopCount == 0) {
                    animatorSet = createAnimator(view, 1);
                }
                this.mPopCount++;
            } else if (enter) {
                animatorSet = createAnimator(view, 2);
            } else {
                animatorSet = createAnimator(view, 3);
            }
        }
        return animatorSet;
    }

    private AnimatorSet createAnimator(View view, int type) {
        ObjectAnimator translateAnimator;
        TranslateAnimatorListener animatorListener;
        AnimatorSet animatorSet;
        if (type == 0) {
            view.setTranslationZ(HwFragmentMenuItemView.ALPHA_NORMAL);
            translateAnimator = ObjectAnimator.ofFloat(view, "translationX", new float[]{(float) this.mAnimatorWidth, BLUR_LAYER_TRANSPARENT});
            translateAnimator.setInterpolator(INTERPOLATOR_20_90);
            translateAnimator.setDuration(300);
            animatorListener = new TranslateAnimatorListener();
            animatorListener.setView(view);
            translateAnimator.addListener(animatorListener);
            animatorSet = new AnimatorSet();
            animatorSet.play(translateAnimator);
            return animatorSet;
        } else if (type == 1) {
            view.setTranslationZ(-1.0f);
            translateAnimator = ObjectAnimator.ofFloat(view, "translationX", new float[]{BLUR_LAYER_TRANSPARENT, ((float) (-this.mAnimatorWidth)) / 2.0f});
            translateAnimator.setInterpolator(INTERPOLATOR_33_33);
            translateAnimator.setDuration(300);
            createBlurLayerAnimator(view, BLUR_LAYER_TRANSPARENT, 0.4f);
            animatorSet = new AnimatorSet();
            animatorSet.playTogether(new Animator[]{translateAnimator, this.mBlurLayerAnimator});
            return animatorSet;
        } else if (type == 2) {
            view.setTranslationZ(-1.0f);
            translateAnimator = ObjectAnimator.ofFloat(view, "translationX", new float[]{((float) (-this.mAnimatorWidth)) / 2.0f, BLUR_LAYER_TRANSPARENT});
            translateAnimator.setInterpolator(INTERPOLATOR_20_90);
            translateAnimator.setDuration(300);
            createBlurLayerAnimator(view, 0.4f, BLUR_LAYER_TRANSPARENT);
            animatorSet = new AnimatorSet();
            animatorSet.playTogether(new Animator[]{translateAnimator, this.mBlurLayerAnimator});
            return animatorSet;
        } else if (type != 3) {
            return null;
        } else {
            view.setTranslationZ(HwFragmentMenuItemView.ALPHA_NORMAL);
            translateAnimator = ObjectAnimator.ofFloat(view, "translationX", new float[]{BLUR_LAYER_TRANSPARENT, (float) this.mAnimatorWidth});
            translateAnimator.setInterpolator(INTERPOLATOR_20_90);
            translateAnimator.setDuration(300);
            animatorListener = new TranslateAnimatorListener();
            animatorListener.setView(view);
            translateAnimator.addListener(animatorListener);
            animatorSet = new AnimatorSet();
            animatorSet.play(translateAnimator);
            return animatorSet;
        }
    }

    private void createBlurLayerAnimator(View view, float startAlpha, final float endAlpha) {
        if (this.mBlurLayerAnimator != null) {
            this.mBlurLayerAnimator.removeAllUpdateListeners();
        }
        this.mBlurLayerAnimator = ObjectAnimator.ofFloat(view, "blurlayer", new float[]{startAlpha, endAlpha});
        this.mBlurLayerAnimator.setInterpolator(INTERPOLATOR_20_90);
        this.mBlurLayerAnimator.setDuration(300);
        this.mFragmentLayout.getRightBlurLayer().setTranslationZ(BLUR_LAYER_TRANSPARENT);
        this.mFragmentLayout.getRightBlurLayer().setVisibility(0);
        this.mFragmentLayout.getRightBlurLayer().setLayerType(2, null);
        this.mBlurLayerAnimator.addUpdateListener(new AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                if (animation != null && animation.getAnimatedValue() != null) {
                    float alphaValue = ((Float) animation.getAnimatedValue()).floatValue();
                    animation.getCurrentPlayTime();
                    HwFragmentContainer.this.mFragmentLayout.getRightBlurLayer().setAlpha(alphaValue);
                    if (((double) Math.abs(endAlpha - alphaValue)) < 0.001d) {
                        HwFragmentContainer.this.mFragmentLayout.getRightBlurLayer().setVisibility(8);
                        HwFragmentContainer.this.mFragmentLayout.getRightBlurLayer().setLayerType(0, null);
                    }
                }
            }
        });
    }
}
