package com.ryantang.pulllistview;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ryantang.pulllistview.RTPullListView.OnLoadMoreDataListener;
import com.ryantang.pulllistview.RTPullListView.OnRefreshListener;

/**
 * PullListView
 * @author Ryan
 *
 */
public class RTPullListViewActivity extends Activity {
	private static final int REFRESH_SUCCESS = 0;
	private static final int LOAD_SUCCESS = 1;
	
	

	
	private RTPullListView pullListView;
	private Activity mActivity;
	private List<String> dataList;
	private ArrayAdapter<String> adapter;
	
	private LinearLayout footParent;
	private View footView;
	private ProgressBar moreProgressBar;
	private TextView footTV;
	
	private void initListView() {

		/** 添加footer */
		LayoutInflater inflater = LayoutInflater.from(this);
		View view = inflater.inflate(R.layout.pulllist_footer, null);
		footParent = new LinearLayout(mActivity);
		footView = view.findViewById(R.id.list_footview);
		moreProgressBar = (ProgressBar) view.findViewById(R.id.footer_progress);
		footTV = (TextView) view.findViewById(R.id.text_view);
		footParent.addView(footView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
		pullListView.addFooterView(footParent);
		pullListView.setAdapter(adapter);

		pullListView.setonLoadMoreDataListener(new OnLoadMore());
		pullListView.setonRefreshListener(new OnRefreshListener() {
			@Override
			public void onRefresh() {
				new Thread(new Runnable() {
					
					@Override
					public void run() {
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						List<String> tmplist= new ArrayList<String>();
						tmplist.addAll(dataList);
						dataList.clear();
						for (int i = 0; i < 5; i++) {
							dataList.add("New Item data "+i);
						}
						dataList.addAll(tmplist);
						mHandler.sendEmptyMessage(REFRESH_SUCCESS);
					}
				}).start();
				
			}
		});
	}
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mActivity=this;
        pullListView = (RTPullListView) this.findViewById(R.id.pullListView);
        
        dataList = new ArrayList<String>();
		for (int i = 0; i < 2; i++) {
			dataList.add("Item data "+i);
		}
		adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, dataList);
		pullListView.setAdapter(adapter);
		initListView();
    }
    
    //结果处理
    private Handler mHandler = new Handler(){

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
			case LOAD_SUCCESS:
				pullListView.onLoadMoreCompleted();
				adapter.notifyDataSetChanged();
				break;
			
			case REFRESH_SUCCESS:
				pullListView.onRefreshComplete();
				adapter.notifyDataSetChanged();
				break;
			default:
				break;
			}
		}
    	
    };
    private int count=0;
    
 // ListView 监听器
 	class OnLoadMore implements OnLoadMoreDataListener {

 		@Override
 		public void onLoadNoMoreData() {
 			footTV.setText("没有更多数据了");
 			moreProgressBar.setVisibility(View.INVISIBLE);
 		}

 		@Override
 		public void onLoadMore() {
 			new Thread(new Runnable() {
				
				@Override
				public void run() {
					try {
						Thread.sleep(2000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
		 			if(count<10){
		 				for (int i = 0; i < 5; i++) {
		 					dataList.add("old item data "+i);
		 				}
		 				count+=5;
		 			}else{
		 				pullListView.setHaveNoMoreFlag(true);// 以后不再显示加载更多的圈圈
		 			}
		 			mHandler.sendEmptyMessage(LOAD_SUCCESS);					
				}
			}).start();
 			
 		}

 		@Override
 		public void setVisiableFooter(final boolean flag) {
 			mActivity.runOnUiThread(new Runnable() {
 				@Override
 				public void run() {
 					if (!flag)
 						footView.setVisibility(View.GONE);
 					else
 						footView.setVisibility(View.VISIBLE);
 				}
 			});

 		}
 	}
}