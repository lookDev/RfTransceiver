package com.rftransceiver.customviews;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeOption;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.rftransceiver.R;

/**
 * Created by rth on 15-8-5.
 */
public class ListItemMapView extends RelativeLayout {

    private BaiduMap baiduMap;  //百度地图
    private GeoCoder geoCoder;  //编码和翻遍码
    private boolean haveGeo = false;    //标识有没有定位位置

    //地理编码的监听器
    private final OnGetGeoCoderResultListener getGeoCoderResultListener = new OnGetGeoCoderResultListener() {
        @Override
        public void onGetGeoCodeResult(GeoCodeResult geoCodeResult) {
            if(geoCodeResult == null || geoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                return;
            }
            if(baiduMap == null)  {
                MapView mapView = (MapView)getChildAt(0);
                baiduMap = mapView.getMap();
            }
            if(baiduMap != null) {
                LatLng ll = geoCodeResult.getLocation();
                if(ll == null) return;
                //构建marker图标
                BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromResource(R.drawable.location_me);
                //构建markerOptions，用于在地图上显示
                OverlayOptions overlayOptions = new MarkerOptions()
                        .position(ll).icon(bitmapDescriptor);
                //显示在地图上
                baiduMap.addOverlay(overlayOptions);
                MapStatusUpdate u = MapStatusUpdateFactory.newLatLng(ll);
                baiduMap.animateMapStatus(u);
            }
        }

        @Override
        public void onGetReverseGeoCodeResult(ReverseGeoCodeResult reverseGeoCodeResult) {

        }
    };

    public ListItemMapView(Context context) {
        super(context);
        initView(context);
    }

    public ListItemMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public ListItemMapView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    /**
     *  加载并初始化视图
     * @param context
     */
    private void initView(Context context) {
        try {
            geoCoder = GeoCoder.newInstance();
            geoCoder.setOnGetGeoCodeResultListener(getGeoCoderResultListener);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * 设置要显示的地址
     * @param address
     */
    public void setAddress(String address) {
        //解析出城市和具体位置并显示
        if(!haveGeo) {
            haveGeo = true;
            String[] addresses = address.split("\\|");
            TextView add = (TextView)getChildAt(1);
            add.setText(addresses[0]);
            if (addresses.length == 2 && geoCoder != null) {
                geoCoder.geocode(new GeoCodeOption().address(addresses[0]).city(addresses[1]));
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
    }
}
