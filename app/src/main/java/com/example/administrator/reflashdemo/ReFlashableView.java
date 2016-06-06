package com.example.administrator.reflashdemo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Created by Administrator on 2016/6/5 0005.
 */
public class ReFlashableView extends LinearLayout implements View.OnTouchListener {
    //    下拉状态
    public static final  int    STATUS_PULL_TO_REFRESH    = 0;
    //    释放立即刷新状态
    public static final  int    STATUS_RELEASE_TO_REFRESH = 1;
    //    正在刷新状态
    public static final  int    STATUS_REFRESHING         = 2;
    //    刷新完成或未刷新状态
    public static final  int    STATUS_REFRESH_FINISHED   = 3;
    //    下拉头部回滚的速度
    public static final  int    SCROLL_SPEED              = -20;
    //    一分钟的毫秒值,用于判断上次更新的时间
    public static final  long   ONE_MINUTE                = 60 * 1000;
    //    一小时的毫秒值,用于判断上次更新的时间
    public static final  long   ONE_HOUR                  = 60 * ONE_MINUTE;
    //    一天的毫秒值,用于判断上次更新的时间
    public static final  long   ONE_DAY                   = 60 * ONE_HOUR;
    //    一个月的毫秒值,用于判断上次更新的时间
    public static final  long   ONE_MONTH                 = 30 * ONE_DAY;
    //    一年的毫秒值,用于判断上次更新的时间
    public static final  long   ONE_YEAR                  = 12 * ONE_MONTH;
    //    上次更新时间的字符串常量,用于作为SharedPreferences的键值
    private static final String UPDATE_AT                 = "update_at";
    //    下拉刷新的回调接口
    private PullToRefreshListener mListener;
    //    用于存储上次更新的时间
    private SharedPreferences     preferences;
    //    下拉头的View
    private View                  header;
    //    需要去下拉刷新的ListView
    private ListView              mListView;
    //    刷新是显示的进度条
    private ProgressBar           mProgressBar;
    //指示下拉和释放的箭头
    private ImageView             arrow;
    //    指示下拉和释放的文字描述
    private TextView              description;
    //    上次更新时间的文字描述
    private TextView              updateAt;
    //    下拉头的布局参数
    private MarginLayoutParams    headerLayoutParams;
    //    上次更新时间的毫秒值
    private long                  lastUpdateTime;
    //    为了防止不同界面的下拉刷新在上次更新时间上互相冲突,使用id来做区分
    private int mId = -1;
    //    下拉头的高度
    private int hideHeaderHeight;

    /**
     * 当前处于什么状态,可选值有STATUS_PULL_REFRESH,STATUS_RELEASE_TO_REFRESH,
     * STATUS_REFRESHING,STATUS_REFRESH_FINISHED
     */
    private int currentStatus = STATUS_REFRESH_FINISHED;
    //    记录上一次的状态是什么,避免重复操作
    private int lastStatus    = currentStatus;
    //    手指按下时,的屏幕纵坐标
    private float   yDown;
    //    在被判定为滚动之前用户手指可以移动的最大值
    private int     touchSlop;
    //    是否已加载过一次layout,这里onLayout中的初始化只需加载一次
    private boolean loadOnce;
    //    当前是否可以下拉,只有listView滚动到头的时候才允许下拉
    private boolean ableToPull;


    public ReFlashableView(Context context) {
        super(context);
    }

    /**
     * 下拉刷新控件的构造函数,会在运行时动态添加一个下拉头的布局
     *
     * @param context
     * @param attrs
     */
    public ReFlashableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        header = LayoutInflater.from(context).inflate(R.layout.pull_to_reflash, null);
        mProgressBar = (ProgressBar) header.findViewById(R.id.progress_bar);
        arrow = (ImageView) header.findViewById(R.id.arrow);
        description = (TextView) header.findViewById(R.id.description);
        updateAt = (TextView) header.findViewById(R.id.update_at);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        refreshUpdateAtValue();
        setOrientation(VERTICAL);
        addView(header, 0);
    }

    /**
     * 进行一些关键的初始化操作,比如讲下拉头向上偏移隐藏,给ListView注册Touch事件
     *
     * @param changed
     * @param l
     * @param t
     * @param r
     * @param b
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && !loadOnce) {
            hideHeaderHeight = -header.getHeight();
            headerLayoutParams = (MarginLayoutParams) header.getLayoutParams();
            headerLayoutParams.topMargin = hideHeaderHeight;
            mListView = (ListView) getChildAt(1);
            mListView.setOnTouchListener(this);
            loadOnce = true;
        }
    }

    /**
     * 当ListView被触摸时,其中处理了各种下拉刷新的具体逻辑
     *
     * @param v
     * @param event
     * @return
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        setIsAbleToPull(event);
        if (ableToPull) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    yDown = event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float yMove = event.getRawY();
                    int distance = (int) (yMove - yDown);
//                    如果手指是下滑状态,并且下拉头是完全隐藏的,就屏蔽下拉事件
                    if (distance <= 0 && headerLayoutParams.topMargin <= hideHeaderHeight) {
                        return false;
                    }
                    if (currentStatus != STATUS_REFRESHING) {
                        if (headerLayoutParams.topMargin > 0) {
                            currentStatus = STATUS_RELEASE_TO_REFRESH;
                        } else {
                            currentStatus = STATUS_RELEASE_TO_REFRESH;
                        }
//                        通过偏移下拉头的topMargin值,来实现下拉效果
                        headerLayoutParams.topMargin = (distance / 2) + hideHeaderHeight;
                        header.setLayoutParams(headerLayoutParams);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                default:
                    if (currentStatus == STATUS_RELEASE_TO_REFRESH) {
//                        松手时如果是释放立即刷新状态,就去调用正在刷新的任务
                        new RefreshingTask().execute();
                    } else if (currentStatus == STATUS_PULL_TO_REFRESH) {
//                        松手时如果是下拉状态,就去调用隐藏下拉头的任务
                        new HideHeaderTask().execute();
                    }
                    break;
            }
//            时刻记得更新下拉头中的信息
            if (currentStatus == STATUS_PULL_TO_REFRESH || currentStatus == STATUS_RELEASE_TO_REFRESH) {
                updateHeaderView();
//                当前正处于下拉或释放状态,要让ListView失去焦点,否则被点击的那一项会一直处于选中状态
                mListView.setPressed(false);
                mListView.setFocusable(false);
                mListView.setFocusableInTouchMode(false);
                lastStatus = currentStatus;
//                当前正处于下拉或释放状态,通过返回true屏蔽掉ListView的滚动事件
                return true;
            }
        }
        return false;
    }

    /**
     * 给下拉刷新控件注册一个监听器
     *
     * @param listener
     * @param id       为防止不同界面的下拉刷新在上次更新时间上互相冲突
     */
    public void setOnRefreshListener(PullToRefreshListener listener, int id) {
        mListener = listener;
        mId = id;
    }

    /**
     * 当所有的刷新逻辑完成后,记录调用一下,否则你的ListView将一直处于正在刷新状态
     */
    public void finishRefreshing() {
        currentStatus = STATUS_REFRESH_FINISHED;
        preferences.edit().putLong(UPDATE_AT + mId, System.currentTimeMillis());
        new HideHeaderTask().execute();
    }

    /**
     * 根据当前的listView的滚动状态来设定{@link #ableToPull}的值
     * 每次都需要在onTouch中第一个执行,这样可以判断出当前应该是滚动listView,还是应该进行下拉
     *
     * @param event
     */
    public void setIsAbleToPull(MotionEvent event) {
        View firstChild = mListView.getChildAt(0);
        if (firstChild != null) {
            int firstVisiblePos = mListView.getFirstVisiblePosition();
            if (firstVisiblePos == 0 && firstChild.getTop() == 0) {
                if (!ableToPull) {
                    yDown = event.getRawY();
                }
                ableToPull = true;
            } else {
                if (headerLayoutParams.topMargin != hideHeaderHeight) {
                    headerLayoutParams.topMargin = hideHeaderHeight;
                    header.setLayoutParams(headerLayoutParams);
                }
                ableToPull = false;
            }
        } else {
//            如果LitsView中没有元素,也应该允许下拉刷新
            ableToPull = true;
        }
    }

    /**
     * 更新下拉头中的信息
     */
    private void updateHeaderView() {
        if (lastStatus != currentStatus) {
            if (currentStatus == STATUS_PULL_TO_REFRESH) {
                description.setText("下拉可以刷新");
                arrow.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.GONE);
                rotateArrow();
            } else if (currentStatus == STATUS_RELEASE_TO_REFRESH) {
                description.setText("释放立即刷新");
                arrow.setVisibility(View.VISIBLE);
                mProgressBar.setVisibility(View.GONE);
                rotateArrow();
            } else if (currentStatus == STATUS_REFRESHING) {
                description.setText("正在刷新...");
                arrow.setVisibility(View.GONE);
                mProgressBar.setVisibility(View.VISIBLE);
                arrow.clearAnimation();
            }
            refreshUpdateAtValue();
        }
    }

    /**
     * 根据当前的状态旋转箭头
     */
    private void rotateArrow() {
        float pivotX = arrow.getWidth() / 2f;
        float pivotY = arrow.getHeight() / 2f;
        float fromDegrees = 0f;
        float toDegreees = 0f;
        if (currentStatus == STATUS_PULL_TO_REFRESH) {
            fromDegrees = 180f;
            toDegreees = 360f;
        } else if (currentStatus == STATUS_RELEASE_TO_REFRESH) {
            fromDegrees = 0f;
            toDegreees = 180f;
        }
        RotateAnimation animation = new RotateAnimation(fromDegrees, toDegreees, pivotX, pivotY);
        animation.setDuration(100);
        animation.setFillAfter(true);
        arrow.startAnimation(animation);
    }

    /**
     * 刷新下拉头中上次更新时间的描述
     */
    private void refreshUpdateAtValue() {
        lastUpdateTime = preferences.getLong(UPDATE_AT + mId, -1);
        long currentTime = System.currentTimeMillis();
        long timePassed = currentTime - lastUpdateTime;
        long timeIntoFormat;
        String updateAtValue;
        if (lastUpdateTime == -1) {
            updateAtValue = "暂未更新过";
        } else if (timePassed < 0) {
            updateAtValue = "时间有问题";
        } else if (timePassed < ONE_MINUTE) {
            updateAtValue = "刚刚更新";
        } else if (timePassed < ONE_HOUR) {
            timeIntoFormat = timePassed / ONE_MINUTE;
            String value = timeIntoFormat + "分钟";
            updateAtValue = String.format("上次更新于...前", value);
        } else if (timePassed < ONE_DAY) {
            timeIntoFormat = timePassed / ONE_HOUR;
            String value = timeIntoFormat + "小时";
            updateAtValue = String.format("上次更新于...前", value);
        } else if (timePassed < ONE_MONTH) {
            timeIntoFormat = timePassed / ONE_DAY;
            String value = timeIntoFormat + "天";
            updateAtValue = String.format("上次更新于...前", value);
        } else {
            timeIntoFormat = timePassed / ONE_MONTH;
            String value = timeIntoFormat + "月";
            updateAtValue = String.format("上次更新于...前", value);
        }
        updateAt.setText(updateAtValue);

    }

    /**
     * 正在刷新的任务,在此任务中会去回调注册进来的下拉刷新监听器
     */
    class RefreshingTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            int topMargin = headerLayoutParams.topMargin;
            while (true) {
                topMargin = topMargin + SCROLL_SPEED;
                if (topMargin <= 0) {
                    topMargin = 0;
                    break;
                }
                publishProgress(topMargin);
            }
            currentStatus = STATUS_REFRESHING;
            publishProgress(0);
            if (mListener != null) {
                mListener.onRefresh();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... topMargin) {
            updateHeaderView();
            headerLayoutParams.topMargin = topMargin[0];
            header.setLayoutParams(headerLayoutParams);
        }
    }

    /**
     * 隐藏下拉头的任务,当未进行下拉刷新或下拉刷新完成后,此任务将会使下拉头重新隐藏
     */
    class HideHeaderTask extends AsyncTask<Void, Integer, Integer> {

        @Override
        protected Integer doInBackground(Void... params) {
            int topMargin = headerLayoutParams.topMargin;
            while (true) {
                topMargin = topMargin + SCROLL_SPEED;
                if (topMargin <= hideHeaderHeight) {
                    topMargin = hideHeaderHeight;
                    break;
                }
                publishProgress(topMargin);
            }
            return topMargin;
        }

        @Override
        protected void onProgressUpdate(Integer... topMargin) {
            headerLayoutParams.topMargin = topMargin[0];
            header.setLayoutParams(headerLayoutParams);
        }

        @Override
        protected void onPostExecute(Integer topMargin) {
            headerLayoutParams.topMargin = topMargin;
            header.setLayoutParams(headerLayoutParams);
            currentStatus = STATUS_REFRESH_FINISHED;
        }
    }

    /**
     * 使当前线程睡眠指定的毫秒数
     *
     * @param time 睡眠时间,单位为毫秒
     */
    private void sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 下拉刷新监听器,使用下拉刷新的地方应该注册此监听器来获取刷新回调
     */
    public interface PullToRefreshListener {
        /**
         * 刷新时会调此方法,在方法内编写具体的刷新逻辑,注意此方法是在子线程中调用的,
         * 可以不必另开线程来进行耗时操作
         */
        void onRefresh();
    }


}
