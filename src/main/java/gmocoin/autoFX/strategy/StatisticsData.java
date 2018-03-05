package gmocoin.autoFX.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONException;
import org.json.JSONObject;

import gmocoin.autoFX.control.Control;

public class StatisticsData {
	private static final int AVG5;
	private static final int AVG25;
	private static final int AVG75;
	
	private static final int SS5 = 500;
	private static final int SS25 = 900;
	private static final int SS75 = 1300;
	private static Control control;
	static{
		control = Control.getInstance();
		AVG5 = Integer.valueOf(control.getProperty("AVG5"));
		AVG25 = Integer.valueOf(control.getProperty("AVG25"));
		AVG75 = Integer.valueOf(control.getProperty("AVG75"));
	}
	public static final int  UNKNOWN = -1;
	public static final int  RISE = 0;
	public static final int  CONVEX = 1;
	public static final int  STEADY = 2;
	public static final int  CONCAVITY = 3;
	public static final int  DECLINE = 4;
	
	private List<PriceData> dataList;
	private List<Integer> avg5List;
	private List<Integer> avg25List;
	private List<Integer> avg75List;
	
	private int max5 = 0;
	private int max25 = 0;
	private int max75 = 0;
	
	private int min5 = 0;
	private int min25 = 0;
	private int min75 = 0;
	
	private int trend5 = -1;
	private int trend25 = -1;
	private int trend75 = -1;
	
	private double slop5 = -1;
	private double slop25 = -1;
	private double slop75 = -1;
	
	private double prevSlop5 = -1;
	private double prevSlop25 = -1;
	private double prevSlop75 = -1;
	
	private int current = 0;
	
	private static StatisticsData instance;
	private static int MAX_SIZE = 10000;
	private StatisticsData(){
		this.dataList = new ArrayList<>();
		this.avg5List = new ArrayList<>();
		this.avg25List = new ArrayList<>();
		this.avg75List = new ArrayList<>();
	}
	public static StatisticsData getInstance(){
		if(instance == null){
			instance = new StatisticsData();
		}
		return instance;
	}
	
	public static StatisticsData getTestInstance(){
		return new StatisticsData();
	}
	
	public void add(PriceData val){
		if(this.dataList.size() > 0){
			PriceData pevData = this.dataList.get(this.dataList.size() - 1);
			if (!pevData.datetime.equals(val.datetime)){
				this.dataList.add(val);
				setAvg(this.avg5List,AVG5,pevData);
				setAvg(this.avg25List,AVG25,pevData);
				setAvg(this.avg75List,AVG75,pevData);
				
				int[] maxmin5 = getMaxMin(AVG5);
				int[] maxmin25 = getMaxMin(AVG25);
				int[] maxmin75 = getMaxMin(AVG75);
				this.max5 = maxmin5[0];
				this.min5 = maxmin5[1];
				this.max25 = maxmin25[0];
				this.min25 = maxmin25[1];
				this.max75 = maxmin75[0];
				this.min75 = maxmin75[1];

				this.slop5 = getSlop(AVG5, this.avg5List,0);
				this.slop25 = getSlop(AVG5, this.avg25List,0);
				this.slop75 = getSlop(AVG5, this.avg75List,0);
				
				this.prevSlop5 = getSlop(AVG5, this.avg5List,AVG5);
				this.prevSlop25 = getSlop(AVG5, this.avg25List,AVG25);
				this.prevSlop75 = getSlop(AVG5, this.avg75List,AVG75);
				
				this.trend5 = getTrend(this.avg5List,AVG5,getS5(),SS5,slop5);
				this.trend25 = getTrend(this.avg25List,AVG25,getS25(),SS25,trend25);
				this.trend75 = getTrend(this.avg75List,AVG75,getS75(),SS75,trend75);
//				System.out.println("date:"+(Long.valueOf(val.datetime) + 900l));
//				System.out.println("this.trend5:"+this.trend5);
//				System.out.println("this.trend25:"+this.trend25);
//				System.out.println("this.trend75:"+this.trend75);
//				System.out.println();
				
//				System.out.println("date:"+(Long.valueOf(val.datetime) + 900l));
//				System.out.println("this.slop5:"+this.slop5);
//				System.out.println("this.slop25:"+this.slop25);
//				System.out.println("this.slop75:"+this.slop75);
//				System.out.println();
				
				if(this.dataList.size() > MAX_SIZE){
					for (int i=0 ; i<1000; i++){
						this.dataList.remove(0);
						this.avg5List.remove(0);
						this.avg25List.remove(0);
						this.avg75List.remove(0);
					}
				}
			}else{
				this.dataList.remove(pevData);
				this.dataList.add(val);
			}
		}else{
			this.dataList.add(val);
		}
		
		this.current = val.closePrice;
	}
	
	private void setAvg( List<Integer> list,int width,PriceData val){
		if (this.dataList.size() > width){
			int avg = 0;
			for(int i = 1;i<=width;i++){
				avg += this.dataList.get(this.dataList.size() - i).getAvg();
			}
			avg = avg/width;
			list.add(avg);
		}
	}
	
	private int[] getMaxMin(int width){
		int max = 0;
		int min = this.dataList.get(0).lowPrice;
		int[] result = new int[2];
		if (width > this.dataList.size()){
			width = this.dataList.size();
		}
		for(;width > 0;width--){
			PriceData val = this.dataList.get(dataList.size() - width);
			if(val.highPrice > max){
				max = val.highPrice;
			}
			if(val.lowPrice < min){
				min = val.lowPrice;
			}
		}
		result[0] = max;
		result[1] = min;
		return result;
	}
	
	private int getTrend(List<Integer> list,int width,int s, int ss, double slop){
		if(list.size() < width){
			return -1;
		}
		int open = list.get(list.size() - width);
		int close =list.get(list.size() - 1);
		int avg = 0;
		
		int max = 0;
		int min = 0;

		for (int i = width;i>0;i-- ){
			int tmp = list.get(list.size() - i);
			avg += tmp;
			if (tmp > max){
				max = tmp;
			}
			if (tmp < min || min == 0){
				min = tmp;
			}
		}
		avg /= width;
		
		min += (300 + width*1.5);
		max -= (300 + width*1.5);
		if (s < ss){
			return STEADY;
		}
		if (open <= min && max <= close && avg > open && avg < close && slop > 10){
			return RISE;
		}
		if (open >= max && min >= close && avg < open && avg > close&& slop < -10){
			return DECLINE;
		}

		int a = (close - open) / width;
		int b = avg - width/2*a;
		
		int f = 0;
		int m = 0;
		int l = 0;
		
		boolean fs1 = false;
		boolean fs2 = false;
		boolean ms1 = false;
		boolean ms2 = false;
		boolean ls1 = false;
		boolean ls2 = false;
		
		for (int i = width;i>0;i-- ){
			int tmp = list.get(list.size() - i);
			int rul = a*i+b;

			if (i == width){
				fs1 = (tmp>(rul+ss));
				fs2 = (tmp<(rul-ss));
			}
			if (i == width/2){
				ms1 = (tmp<(rul-ss));
				ms2 = (tmp>(rul+ss));
			}
			if (i == 1){
				ls1 = (tmp>(rul+ss));
				ls2 = (tmp<(rul-ss));
			}
			if (i < width/4){
				if(tmp>=rul) f++;
			}
			if (i >= width/4 && i <= width/4*3){
				if (tmp <= rul) m++;
			}
			if (i > width/4*3){
				if(tmp >= rul) l++;
			}
		}
		

		if (f<(width/10) && m < (width/5) && l <(width/10) && fs2 && ls2 && ms2){
			return CONVEX;
		}
		if (f>(width/20*3) && m > (width/10*3) && l >(width/20*3) && fs1 && ls1 && ms1){
			return CONCAVITY;
		}
		return UNKNOWN;
		
	}
	
	public PriceData getbVal(int i){
		if((dataList.size() - 1 - i) >= 0){
			return dataList.get((dataList.size() - 1) - i);
		}else{
			return null;
		}
	}
	public int getbAvg5Val(int i){
		if((avg5List.size() - 1 - i) >= 0){
			return avg5List.get((avg5List.size() - 1) - i);
		}else{
			return 0;
		}
	}
	public int getbAvg25Val(int i){
		if((avg25List.size() - 1 - i) >= 0){
			return avg25List.get((avg25List.size() - 1) - i);
		}else{
			return 0;
		}
	}
	public int getbAvg75Val(int i){
		if((avg75List.size() - 1 - i) >= 0){
			return avg75List.get((avg75List.size() - 1) - i);
		}else{
			return 0;
		}
	}
	public PriceData getLastVal(){
		if (dataList.size() == 0){
			return new PriceData();
		}
		return dataList.get(dataList.size() - 1);
	}
	public int getLastAvg5Val(){
		if (avg5List.size() == 0){
			return 0;
		}
		return avg5List.get(avg5List.size() - 1);
	}
	public int getLastAvg25Val(){
		if (avg25List.size() == 0){
			return 0;
		}
		return avg25List.get(avg25List.size() - 1);
	}
	public int getLastAvg75Val(){
		if (avg75List.size() == 0){
			return 0;
		}
		return avg75List.get(avg75List.size() - 1);
	}
	public int getMax5(){
		return max5;
	}
	public int getMin5(){
		return min5;
	}
	public int getMax25(){
		return max25;
	}
	public int getMin25(){
		return min25;
	}
	public int getMax75(){
		return max75;
	}
	public int getMin75(){
		return min75;
	}
	public int getCurrent(){
		return this.current;
	}
	public int getTrend5(){
		return trend5;
	}
	public int getTrend25(){
		return trend25;
	}
	public int getTrend75(){
		return trend75;
	}
	public double getSlop5() {
		return slop5;
	}
	public double getSlop25() {
		return slop25;
	}
	public double getSlop75() {
		return slop75;
	}
	public double getPrevSlop5() {
		return prevSlop5;
	}
	public double getPrevSlop25() {
		return prevSlop25;
	}
	public double getPrevSlop75() {
		return prevSlop75;
	}
	public int getS5() {
		long result = 0;
		long tmp = 0;
		int lastAvg = getLastAvg5Val();
		if (this.dataList.size() < AVG5){
			return 0;
		}
		for(int i=1;i<=AVG5;i++){
			PriceData pd = this.dataList.get(this.dataList.size() - i);
			tmp = pd.getAvg() - lastAvg;
			tmp *= tmp;
			result += tmp;
		}
		return (int) Math.pow(result/AVG5, 0.5);
	}
	public int getS25() {
		long result = 0;
		long tmp = 0;
		int lastAvg = getLastAvg25Val();
		if (this.dataList.size() < AVG25){
			return 0;
		}
		for(int i=1;i<=AVG25;i++){
			PriceData pd = this.dataList.get(this.dataList.size() - i);
			tmp = pd.getAvg() - lastAvg;
			tmp *= tmp;
			result += tmp;
		}
		return (int) Math.pow(result/AVG25, 0.5);
	}
	public int getS75() {
		long result = 0;
		long tmp = 0;
		int lastAvg = getLastAvg75Val();
		if (this.dataList.size() < AVG75){
			return 0;
		}
		for(int i=1;i<=AVG75;i++){
			PriceData pd = this.dataList.get(this.dataList.size() - i);
			tmp = pd.getAvg() - lastAvg;
			tmp *= tmp;
			result += tmp;
		}
		return (int) Math.pow(result/AVG75, 0.5);
	}
	private double getSlop(int width, List<Integer> y, int offset) { 
		if(width+offset > y.size()){
			return 0;
		}
        double slopeValue = 0; 
        double avgy = getAverage(width,y,offset); 
        double avgx = width/2; 

        //存∑((arrx(n) – arrx mean) * (arry(n) – arry mean))值 
        double sum1 = 0; 
        //存∑((arrx(n) – arrx mean) ²值 
        double sum2 = 0; 

        for (int i = width; i > 0; i--) { 
            sum1 += ((width - i + 1)  - avgx)*((y.get(y.size() - i - offset) - avgy)); 
            sum2 += Math.pow((width - i + 1 - avgx), 2); 
        } 
        slopeValue = sum1 / sum2; 
        return slopeValue; 
    }
	
	 private int getAverage(int width, List<Integer> y, int offset) { 
        int sum = 0; 
        for (int i=1;i<=width;i++) { 
            sum += y.get(y.size() - i - offset); 
        } 
        return sum / width; 
	 }
	public List<PriceData> getDataList() {
		return dataList;
	} 
	 
	 
}
