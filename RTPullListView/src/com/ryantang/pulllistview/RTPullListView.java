package com.ryantang.pulllistview;

/**
 * @author  
 * @modify  by  巨魔 
 * @descripte:  添加加载更多逻辑
 * */

import java.util.Date;





import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class RTPullListView extends ListView implements OnScrollListener {  
	private static final String TAG = "RTPullListView";

	private final static int RELEASE_To_REFRESH = 0;
	private final static int PULL_To_REFRESH = 1;
	private final static int REFRESHING = 2;
	private final static int DONE = 3;
	private final static int LOADING = 4;

	// 实际的padding的距离与界面上偏移距离的比例
	private final static int RATIO = 3;

	private LayoutInflater inflater;

	private LinearLayout headView;

	private TextView tipsTextview;
	private TextView lastUpdatedTextView;
	private ImageView arrowImageView;
	private ProgressBar progressBar;

	private RotateAnimation animation;
	private RotateAnimation reverseAnimation;

	// 用于保证startY的值在一个完整的touch事件中只被记录一次
	private boolean isRecored;

//	private int headContentWidth;
	private int headContentHeight;

	private int startY;
	private int firstItemIndex;
	private int state;
	private boolean isBack;
	private OnRefreshListener refreshListener;
	private OnLoadMoreDataListener loadMoreDataListener;

	private boolean isRefreshable;
	private boolean isPush;

	private int visibleLastIndex;
	private int visibleItemCount;

	/***
	 * true:加载更多时，已经没有数据可以再加载了，此时需要调用者执行 onLoadNoMoreData 
	 * false:默认值，加载更多时没有数据可加载，没有执行过  或者 已经执行过一次onLoadNoMoreData，不需要重复执行onLoadNoMoreData
	 */
	private boolean haveNoMoreFlag=false;    
	private boolean isLoadingMore=false; //是否正在执行加载更多事件
	private boolean isLastRow=false;     //是否是最后一个item
	
	public RTPullListView(Context context) {
		super(context);
		init(context);
	}

	public RTPullListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}
	
	private void init(Context context) {
		inflater = LayoutInflater.from(context);
		headView = (LinearLayout) inflater.inflate(R.layout.pulllist_head, null);
		arrowImageView = (ImageView) headView.findViewById(R.id.head_arrowImageView);
//		arrowImageView.setMinimumWidth(70);
//		arrowImageView.setMinimumHeight(50);
		progressBar = (ProgressBar) headView.findViewById(R.id.head_progressBar);
		tipsTextview = (TextView) headView.findViewById(R.id.head_tipsTextView);
		lastUpdatedTextView = (TextView) headView.findViewById(R.id.head_lastUpdatedTextView);

		measureView(headView);
		headContentHeight = headView.getMeasuredHeight();
//		headContentWidth = headView.getMeasuredWidth();

		headView.setPadding(0, -1 * headContentHeight, 0, 0);
		headView.invalidate();

		addHeaderView(headView, null, false);
		setOnScrollListener(this);

		animation = new RotateAnimation(0, -180,
				RotateAnimation.RELATIVE_TO_SELF, 0.5f,
				RotateAnimation.RELATIVE_TO_SELF, 0.5f);
		animation.setInterpolator(new LinearInterpolator());
		animation.setDuration(250);
		animation.setFillAfter(true);

		reverseAnimation = new RotateAnimation(-180, 0,
				RotateAnimation.RELATIVE_TO_SELF, 0.5f,
				RotateAnimation.RELATIVE_TO_SELF, 0.5f);
		reverseAnimation.setInterpolator(new LinearInterpolator());
		reverseAnimation.setDuration(200);
		reverseAnimation.setFillAfter(true);

		state = DONE;
		isRefreshable = false;
		isPush = true;
	}

	public void setHaveNoMoreFlag(boolean havaNoMoreFlag){
		this.haveNoMoreFlag=havaNoMoreFlag;
	}
	/**
	 * @Descripte: 重置标志位，加载更多执行完毕
	 * */
	public void onLoadMoreCompleted(){
		isLoadingMore=false;
	}
	public boolean setFootVisiableFlag=false; //保证footer的visiable属性只设置一次
	public boolean setFootGoneFlag=false;
	public boolean onLoadNoMoreDataExecute=false;//onLoadNoMoreData 执行标志，true表示执行，false表示没有执行
	/**
	 * Descripte: 实现功能：下来刷新，获取当前数据之后的最新数据；上拉加载更多，获取当前数据之前的“旧”数据
	 * 由于旧数据有限，可能被全部加载完成，因此当旧的数据全部加载完成时，上拉加载更多只显示文字“加载完毕”不再执行加载动作和加载动画，当数据没有加载完成时，需要执行加载更多动作和动画，
	 * 如果没有数据，则通过haveNoMoreFlag进行标记，外部通过setHaveNoMoreFlag方法进行设置
	 * ---------------------------------------------------------------------
	 * 如果第一此就全部加载完毕，没有旧数据，如果当前的数据量没有填充满屏幕，则footer不予显示，如果超过屏幕可装载量， 则footer显示。
	 * -----此时需要考虑的情况是，当第一次加载完成时，没有填充屏幕，footer不显示，但如果通过下来刷新获取到新数据时，超过一个屏幕时，footer允许显示
	 * 
	 * */
	public void onScroll(AbsListView arg0, int firstVisiableItem, int arg2,int arg3) {
		firstItemIndex = firstVisiableItem;
		visibleLastIndex = firstVisiableItem + arg2 - 1;
		visibleItemCount = arg2;
		if (firstItemIndex == 1 && !isPush) {
			setSelection(0);
		}
		//所有数据不满一页或者刚好凑成一页，此时footer不显示"
		//如果footer存在（同时只要存在一个item），则getChildCount一定>3,会把header和footer一同计算,这里减2是因为adapter中包含了header与footer
		if(firstVisiableItem == 0){
			if(getFooterViewsCount()>0/*这个判断是保证list中存在数据时再判断*/
					&& arg2 == arg3 && getAdapter()!=null && getAdapter().getCount()-2<arg2 ){//如果数据不满一页，则当前页面可视数据和所有数据个数相同
				if (!setFootGoneFlag&&loadMoreDataListener != null){
					setFootGoneFlag=true;
					loadMoreDataListener.setVisiableFooter(false);
					return ;
				}
			}
		}
		//上一层if判断 是为了保证初始化时（第一次）如果当前数据没有填充满屏幕，则不显示footer，一旦item数量超过屏幕可显示数据，则显示footer，
		if(arg2!=arg3&& getAdapter()!=null && getAdapter().getCount()-2 > arg2){
			if (!setFootVisiableFlag&&loadMoreDataListener != null){
				setFootVisiableFlag=true;
				loadMoreDataListener.setVisiableFooter(true);
			}
		}
		int lastItemPosition = firstVisiableItem + arg2;
		if (lastItemPosition == arg3) {// 可以显示加载更多
			if (isLoadingMore) {// 如果正在执行加载更多，不再执行
				return;
			}
			if (haveNoMoreFlag) {// 确实没有"旧"数据了
				if (loadMoreDataListener != null && !onLoadNoMoreDataExecute){
					loadMoreDataListener.onLoadNoMoreData();
					onLoadNoMoreDataExecute=true;//onLoadNoMoreData只执行一次，已经将footer设置为 没有更多数据的状态，不再执行了
				}
				return;//这里返回的意思是，服务端已经再没有更多的“旧”数据了，不再需要加载更多的动作了
			}
			
			View lastItemView = (View) this.getChildAt(getChildCount() - 1);
			if (lastItemView!=null && (this.getBottom()) == lastItemView.getBottom()) {
				if (getFooterViewsCount() != 0) {// 执行到这里说明可以执行加载更多了
					isLastRow=true;
					return ;
				}// if (getFooterViewsCount()!=0)
			}
			isLastRow = false;
		}

	}

	public void onScrollStateChanged(AbsListView arg0, int scrollState) {
		 if (isLastRow && scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
			 isLoadingMore = true;
			 if (loadMoreDataListener != null)
					loadMoreDataListener.onLoadMore();
			 isLastRow = false;//清除标记位
		 }
	}
	@Override
	public boolean onTouchEvent(MotionEvent event) {

		if (isRefreshable) {
			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				if (firstItemIndex == 0 && !isRecored) {
					isRecored = true;
					isPush = true;
					startY = (int) event.getY();
					//Log.v(TAG, "在down时候记录当前位置‘");
				}
				break;
			case MotionEvent.ACTION_UP:
				if (state != REFRESHING && state != LOADING) {
					if (state == DONE) {
						// 什么都不做
					}
					if (state == PULL_To_REFRESH) {
						state = DONE;
						changeHeaderViewByState();

						//Log.v(TAG, "由下拉刷新状态，到done状态");
					}
					if (state == RELEASE_To_REFRESH) {
						state = REFRESHING;
						changeHeaderViewByState();
						onRefresh();

						//Log.v(TAG, "由松开刷新状态，到done状态");
					}
				}

				isRecored = false;
				isBack = false;

				break;

			case MotionEvent.ACTION_MOVE:
				int tempY = (int) event.getY();

				if (!isRecored && firstItemIndex == 0) {
					//Log.v(TAG, "在move时候记录下位置");
					isRecored = true;
					startY = tempY;
				}

				if (state != REFRESHING && isRecored && state != LOADING) {

					// 保证在设置padding的过程中，当前的位置一直是在head，否则如果当列表超出屏幕的话，当在上推的时候，列表会同时进行滚动

					// 可以松手去刷新了
					if (state == RELEASE_To_REFRESH) {

						setSelection(0);

						// 往上推了，推到了屏幕足够掩盖head的程度，但是还没有推到全部掩盖的地步
						if (((tempY - startY) / RATIO < headContentHeight)
								&& (tempY - startY) > 0) {
							state = PULL_To_REFRESH;
							changeHeaderViewByState();

							Log.v(TAG, "由松开刷新状态转变到下拉刷新状态");
						}
						// 一下子推到顶了
						else if (tempY - startY <= 0) {
							state = DONE;
							changeHeaderViewByState();

							//Log.v(TAG, "由松开刷新状态转变到done状态");
						}
						// 往下拉了，或者还没有上推到屏幕顶部掩盖head的地步
						else {
							// 不用进行特别的操作，只用更新paddingTop的值就行了
						}
					}
					// 还没有到达显示松开刷新的时候,DONE或者是PULL_To_REFRESH状态
					if (state == PULL_To_REFRESH) {

						setSelection(0);

						// 下拉到可以进入RELEASE_TO_REFRESH的状态
						if ((tempY - startY) / RATIO >= headContentHeight) {
							state = RELEASE_To_REFRESH;
							isBack = true;
							changeHeaderViewByState();
							Log.v(TAG, "由done或者下拉刷新状态转变到松开刷新");
						}
						// 上推到顶了
						else if (tempY - startY <= 0) {
							state = DONE;
							changeHeaderViewByState();
							isPush = false;
							Log.v(TAG, "由DOne或者下拉刷新状态转变到done状态");
						}
					}

					// done状态下
					if (state == DONE) {
						if (tempY - startY > 0) {
							state = PULL_To_REFRESH;
							changeHeaderViewByState();
						}
					}

					// 更新headView的size
					if (state == PULL_To_REFRESH) {
						headView.setPadding(0, -1 * headContentHeight
								+ (tempY - startY) / RATIO, 0, 0);

					}

					// 更新headView的paddingTop
					if (state == RELEASE_To_REFRESH) {
						headView.setPadding(0, (tempY - startY) / RATIO
								- headContentHeight, 0, 0);
					}

				}

				break;
			}
		}

		return super.onTouchEvent(event);
	}

	// 当状态改变时候，调用该方法，以更新界面
	private void changeHeaderViewByState() {
		switch (state) {
		case RELEASE_To_REFRESH:
			arrowImageView.setVisibility(View.VISIBLE);
			progressBar.setVisibility(View.GONE);
			tipsTextview.setVisibility(View.VISIBLE);
			lastUpdatedTextView.setVisibility(View.VISIBLE);

			arrowImageView.clearAnimation();
			arrowImageView.startAnimation(animation);

			tipsTextview.setText(getResources().getString(R.string.release_to_refresh));

			Log.v(TAG, "当前状态，松开刷新");
			break;
		case PULL_To_REFRESH:
			progressBar.setVisibility(View.GONE);
			tipsTextview.setVisibility(View.VISIBLE);
			lastUpdatedTextView.setVisibility(View.VISIBLE);
			arrowImageView.clearAnimation();
			arrowImageView.setVisibility(View.VISIBLE);
			// 是由RELEASE_To_REFRESH状态转变来的
			if (isBack) {
				isBack = false;
				arrowImageView.clearAnimation();
				arrowImageView.startAnimation(reverseAnimation);

				tipsTextview.setText(getResources().getString(R.string.pull_to_refresh));
			} else {
				tipsTextview.setText(getResources().getString(R.string.pull_to_refresh));
			}
			//Log.v(TAG, "当前状态，下拉刷新");
			break;

		case REFRESHING:

			headView.setPadding(0, 0, 0, 0);

			progressBar.setVisibility(View.VISIBLE);
			arrowImageView.clearAnimation();
			arrowImageView.setVisibility(View.GONE);
			tipsTextview.setText(getResources().getString(R.string.refreshing));
			lastUpdatedTextView.setVisibility(View.VISIBLE);

			//Log.v(TAG, "当前状态,正在刷新...");
			break;
		case DONE:
			headView.setPadding(0, -1 * headContentHeight, 0, 0);

			progressBar.setVisibility(View.GONE);
			arrowImageView.clearAnimation();
			arrowImageView.setImageResource(R.drawable.pulltorefresh);
			tipsTextview.setText(getResources().getString(R.string.pull_to_refresh));
			lastUpdatedTextView.setVisibility(View.VISIBLE);

			//Log.v(TAG, "当前状态，done");
			break;
		}
	}

	public void setonRefreshListener(OnRefreshListener refreshListener) {
		this.refreshListener = refreshListener;
		isRefreshable = true;
	}
	//private boolean is
	public void setonLoadMoreDataListener(OnLoadMoreDataListener loadMoreDataListener)
	{
		this.loadMoreDataListener=loadMoreDataListener;
	}
	public interface OnRefreshListener {
		public void onRefresh();
	}
	public interface OnLoadMoreDataListener{
		public void onLoadNoMoreData();
		public void onLoadMore();
		public void setVisiableFooter(final boolean flag);
	}
	public void onRefreshComplete() {
		state = DONE;
		lastUpdatedTextView.setText(getResources().getString(R.string.updating) + new Date().toLocaleString());
		changeHeaderViewByState();
		invalidateViews();
		setSelection(0);
	}

	private void onRefresh() {
		if (refreshListener != null) {
			refreshListener.onRefresh();
		}
	}
	
	public void clickToRefresh(){
		state = REFRESHING;
		changeHeaderViewByState();
	}
	
	// 此方法直接照搬自网络上的一个下拉刷新的demo，此处是“估计”headView的width以及height
	private void measureView(View child) {
		ViewGroup.LayoutParams p = child.getLayoutParams();
		if (p == null) {
			p = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
					ViewGroup.LayoutParams.WRAP_CONTENT);
		}
		int childWidthSpec = ViewGroup.getChildMeasureSpec(0, 0 + 0, p.width);
		int lpHeight = p.height;
		int childHeightSpec;
		if (lpHeight > 0) {
			childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight,
					MeasureSpec.EXACTLY);
		} else {
			childHeightSpec = MeasureSpec.makeMeasureSpec(0,
					MeasureSpec.UNSPECIFIED);
		}
		child.measure(childWidthSpec, childHeightSpec);
	}

	public void setAdapter(BaseAdapter adapter) {
		lastUpdatedTextView.setText(getResources().getString(R.string.updating) + new Date().toLocaleString());
		super.setAdapter(adapter);
	}
}  