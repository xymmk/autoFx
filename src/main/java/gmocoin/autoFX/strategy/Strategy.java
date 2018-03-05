package gmocoin.autoFX.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import gmocoin.autoFX.Collabo.sp.SpService;
import gmocoin.autoFX.control.Control;

public class Strategy{
	private static int DIFFERENCE;
	private static int BUY_SCORE;
	private static int SETTLE_SCORE;
	private static int BUY_SCORE_T;
	private static int SETTLE_SCORE_T;
	private static int lostCut;
	private static Control control;
	static{
		control = Control.getInstance();
		DIFFERENCE = Integer.valueOf(control.getProperty("DIFFERENCE"));
		BUY_SCORE = Integer.valueOf(control.getProperty("buyScore"));
		SETTLE_SCORE = Integer.valueOf(control.getProperty("settleScore"));
		BUY_SCORE_T = Integer.valueOf(control.getProperty("buyScoreT"));
		SETTLE_SCORE_T = Integer.valueOf(control.getProperty("settleScoreT"));
		lostCut = Integer.valueOf(control.getProperty("lostCut"));
	}
	private List<Trade> tradeList;
	private SpService service;
	private float singleQuantity;
	
	private int maxTradeSize;
	private int liveTradeSize = 0;
	
	private int maxFluct = 0;
	private int maxS = 0;
	
	private boolean isTest;
	
	private int backCount = 0;
	

	private int buyScore;
	private int settleScore;
	private int buyScoreT;
	private int settleScoreT;
	
	private StatisticsData sdata;
	
	public Strategy(SpService service, int buyScore, int settleScore, int buyScoreT,int settleScoreT, StatisticsData sdata){
		this.tradeList = new ArrayList<>();
		this.service = service;
		this.maxTradeSize = Integer.valueOf(control.getProperty("maxTradeCount"));
		this.maxFluct = 1500;
		this.maxS = 3000;
		this.singleQuantity = Float.valueOf(control.getProperty("singleQuantity"));
		
		this.buyScore = buyScore;
		this.settleScore = settleScore;
		this.buyScoreT = buyScoreT;
		this.settleScoreT = settleScoreT;
		
		this.sdata = sdata;
		this.isTest = true;
	}
	public Strategy(SpService service, boolean isTest){
		this.tradeList = new ArrayList<>();
		this.service = service;
		this.maxTradeSize = Integer.valueOf(control.getProperty("maxTradeCount"));
		this.maxFluct = 1500;
		this.maxS = 3000;
		this.singleQuantity = Float.valueOf(control.getProperty("singleQuantity"));
		this.sdata = service.getData();
		this.isTest = isTest;
		if(isTest){
			this.buyScore = BUY_SCORE;
			this.buyScoreT = BUY_SCORE_T;
			this.settleScore = SETTLE_SCORE;
			this.settleScoreT = SETTLE_SCORE_T;
		}
	}
	public void run() {
		doTread();
		doSettlement();
	}
	
	private boolean doTread(){
		boolean result = false;
		if (backCount > 0){
			backCount--;
		}
		if(liveTradeSize < maxTradeSize && backCount == 0){
			StatisticsData statisticsData = this.sdata;
			int price = 0;
			int score = 0;
			int sslop75 = (int) (Math.pow(Math.abs(statisticsData.getSlop75()),0.5));
			int avg5 = statisticsData.getLastAvg5Val();
			int avg25 = statisticsData.getLastAvg25Val();
			int avg75 = statisticsData.getLastAvg75Val();
			int prevAvg5 = statisticsData.getbAvg5Val(10);
			int prevAvg25 = statisticsData.getbAvg25Val(10);
			int prevAvg75 = statisticsData.getbAvg75Val(10);
			
			score = isBuy(statisticsData);
			score += (statisticsData.getSlop75() > 0.0?sslop75:-sslop75);
			if ((avg25 - avg75) > 3000) score +=5;
			if((prevAvg5<prevAvg75) && (avg5>prevAvg75))score +=5;
			if((prevAvg25<prevAvg75) && (avg25>prevAvg75))score +=15;
			price = statisticsData.getCurrent()+DIFFERENCE/2;
//			System.out.println("doTread score:"+score + " " +"多");
			newTrade(score,true,statisticsData,isTest?buyScore:BUY_SCORE,isTest?buyScoreT:BUY_SCORE_T);
			
			
			score = isShort(statisticsData);
			score += (statisticsData.getSlop75() > 0.0?-sslop75:sslop75);
			if ((avg75 - avg25) > 3000) score +=5;
			if((prevAvg5>prevAvg75) && (avg5<prevAvg75))score +=5;
			if((prevAvg25>prevAvg75) && (avg25<prevAvg75))score +=15;
			price = statisticsData.getCurrent()-DIFFERENCE/2;
//			System.out.println("doTread score:"+score + " " +("空"));
			newTrade(score,false,statisticsData,isTest?buyScore:BUY_SCORE,isTest?buyScoreT:BUY_SCORE_T);
			
		}
		return result;
	}
	
	private void newTrade(int score,boolean isbuy, StatisticsData statisticsData, int passedScore, int passedScoreT){
		if(score > passedScore && score < passedScoreT){
			int price = 0;
			if(isbuy) {
				price = statisticsData.getCurrent()+DIFFERENCE/2;
			}else{
				price = statisticsData.getCurrent()-DIFFERENCE/2;
			}
			
			if(!this.isTest){
				System.out.println("新建交易:"+(isbuy?"多":"空"));
				System.out.println("Trend5:"+statisticsData.getTrend5());
				System.out.println("Trend25:"+statisticsData.getTrend25());
				System.out.println("Trend75:"+statisticsData.getTrend75());
				System.out.println("Trend5:"+statisticsData.getSlop5());
				System.out.println("Trend25:"+statisticsData.getSlop25());
				System.out.println("Trend75:"+statisticsData.getSlop75());
			}
			Trade trade;
			if (isTest) {
				trade =new Trade(isbuy, statisticsData.getLastVal(), price, this.singleQuantity);
			}else{
				trade = service.newTread(isbuy, this.singleQuantity);
			}
			if(trade != null){
				tradeList.add(trade);
				liveTradeSize++;
				if (isTest){
					backCount = 2;
				}else{
					backCount = 3000;
				}
			}
		}
	}
	
	private boolean doSettlement(){
		boolean result = false;
		StatisticsData statisticsData = this.sdata;
		int currentPrice = statisticsData.getCurrent();
		int price = 0;
		int score = 0;
		int prof = (int) (30f*100.0*this.singleQuantity);
		int avg5 = statisticsData.getLastAvg5Val();
		int avg25 = statisticsData.getLastAvg25Val();
		int avg75 = statisticsData.getLastAvg75Val();
		int prevAvg5 = statisticsData.getbAvg5Val(10);
		int prevAvg25 = statisticsData.getbAvg25Val(10);
		int prevAvg75 = statisticsData.getbAvg75Val(10);
		for (Trade trade:tradeList){
			if(!trade.isSettlement()){
				boolean doSettle = false;
				if(trade.isBuy()){
					price =currentPrice-DIFFERENCE/2;
					score = isShort(statisticsData);
					if ((avg25 - avg75) > 2500) score +=5;
					if((prevAvg5<prevAvg75) && (avg5>prevAvg75))score +=5;
					if((prevAvg25<prevAvg75) && (avg25>prevAvg75))score +=15;
				}else{
					price = currentPrice+DIFFERENCE/2 ;
					score = isBuy(statisticsData);
					if ((avg75 - avg25) > 2500) score +=5;
					if((prevAvg5>prevAvg75) && (avg5<prevAvg75))score +=5;
					if((prevAvg25>prevAvg75) && (avg25<prevAvg75))score +=15;
				}
				
				if (score > (this.isTest?settleScore:SETTLE_SCORE) && score < (this.isTest?settleScoreT:SETTLE_SCORE_T)){
					if(trade.getProfitLoss(price)  > prof){
						doSettle = true;
					}
				}else if (Math.abs(currentPrice*100/trade.getPrice() - 100) >= lostCut){
					doSettle = true;
				}

//				System.out.println("Settle score:"+score + " " + (trade.isBuy()?"多":"空"));
				
				if (doSettle){
					if(!this.isTest){
						System.out.println("新建決済");
						System.out.println("Trend5:"+statisticsData.getTrend5());
						System.out.println("Trend25:"+statisticsData.getTrend25());
						System.out.println("Trend75:"+statisticsData.getTrend75());
						System.out.println("Trend5:"+statisticsData.getSlop5());
						System.out.println("Trend25:"+statisticsData.getSlop25());
						System.out.println("Trend75:"+statisticsData.getSlop75());
					}
					if(isTest){
						trade.doSettlement(price);
						liveTradeSize--;
					}else{
						if(service.settleTread(trade)){
							liveTradeSize--;
						}
					}
				}
			}
		}
		return result;
	}
	
	private int isBuy(StatisticsData statisticsData){
		int flg = 0;

		PriceData currentDate = statisticsData.getLastVal();
		int currentPrice = statisticsData.getCurrent();
		int avg5 = statisticsData.getLastAvg5Val();
		int avg25 = statisticsData.getLastAvg25Val();
		int avg75 = statisticsData.getLastAvg75Val();
		int s25 = statisticsData.getS25();
		int s5 = statisticsData.getS5();
		int s525 = (s25 + s5*3) / 4;
		int sslop5 = (int) Math.pow(Math.abs(statisticsData.getSlop5()),0.333);
		int sslop25 = (int) Math.pow(Math.abs(statisticsData.getSlop25()),0.333);
		int hosei = 0;
		if (statisticsData.getTrend75() == StatisticsData.RISE){
			hosei = s5/3;
		}
		if (statisticsData.getTrend75() == StatisticsData.DECLINE){
			hosei = -s5/3;
		}
		if(s25 < this.maxS) flg += 9;
		if((avg25 - currentPrice + hosei) > s525) flg += 25;
		if(statisticsData.getTrend25() == StatisticsData.CONCAVITY
				|| statisticsData.getTrend25() == StatisticsData.RISE ) flg += 10;
		if(statisticsData.getTrend5() == StatisticsData.RISE) flg += 10;
		
		if(statisticsData.getTrend25() == StatisticsData.DECLINE) flg -= 10;
		if(statisticsData.getTrend5() == StatisticsData.DECLINE) flg -= 10;
		
		if(statisticsData.getSlop5()> 0.0 && statisticsData.getSlop5()*statisticsData.getPrevSlop5() < 0.0)  flg += 25;
		
		flg += (statisticsData.getSlop5() > 0.0?sslop5:-sslop5);
		flg += (statisticsData.getSlop25() > 0.0?sslop25:-sslop25);
		
		return flg;
	}
	
	private int isShort(StatisticsData statisticsData){
		int flg = 0;
		
		PriceData currentDate = statisticsData.getLastVal();
		int currentPrice = statisticsData.getCurrent();
		int avg5 = statisticsData.getLastAvg5Val();
		int avg25 = statisticsData.getLastAvg25Val();
		int avg75 = statisticsData.getLastAvg75Val();
		int s25 = statisticsData.getS25();
		int s5 = statisticsData.getS5();
		int s525 = (s25 + s5*3) / 4;
		int sslop5 = (int) Math.pow(Math.abs(statisticsData.getSlop5()),0.333);
		int sslop25 = (int) Math.pow(Math.abs(statisticsData.getSlop25()),0.333);
		int hosei = 0;
		if (statisticsData.getTrend75() == StatisticsData.RISE){
			hosei = s5/2;
		}
		if (statisticsData.getTrend75() == StatisticsData.DECLINE){
			hosei = -s5/2;
		}
		if(s25 < this.maxS) flg += 9;
		if( (currentPrice - avg25 - hosei) > s525)flg += 25;
		if(statisticsData.getTrend25() == StatisticsData.CONVEX
				|| statisticsData.getTrend25() == StatisticsData.DECLINE) flg += 10;
		if(statisticsData.getTrend5() == StatisticsData.DECLINE) flg += 10;
		
		if(statisticsData.getTrend25() == StatisticsData.RISE) flg -= 10;
		if(statisticsData.getTrend5() == StatisticsData.RISE) flg -= 10;
		

		if(statisticsData.getSlop5() < 0.0 && statisticsData.getSlop5()*statisticsData.getPrevSlop5() < 0.0)  flg += 25;
		
		flg += (statisticsData.getSlop5() < 0.0?sslop5:-sslop5);
		flg += (statisticsData.getSlop25() < 0.0?sslop25:-sslop25);
		
		return flg;
	}
	
	public int getProfitLoss() {
		int current = this.sdata.getCurrent();
		int result = 0;
		for (Trade trade:tradeList){
			if(trade.isBuy()){
				result += trade.getProfitLoss(current-DIFFERENCE/2);
			}else{
				result += trade.getProfitLoss(current+DIFFERENCE/2);
			}
		}
		return result;
	}
	
	public int getValidTradeCount(){
		int count = 0;
		for (Trade trade:tradeList){
			if(!trade.isSettlement()){
				count++;
			}
		}
		return count;
	}
	
	public int getSettledTradeCount(){
		int count = 0;
		for (Trade trade:tradeList){
			if(trade.isSettlement()){
				count++;
			}
		}
		return count;
	}
	public int getLostCutTradeCount(){
		int count = 0;
		for (Trade trade:tradeList){
			if(trade.isSettlement() && trade.getProfitLoss(0) < 0){
				count++;
			}
		}
		return count;
	}
	public int getVaildProfitLoss() {
		int current = this.sdata.getCurrent();
		int result = 0;
		for (Trade trade:tradeList){
			if(trade.isSettlement()){
				continue;
			}
			if(trade.isBuy()){
				result += trade.getProfitLoss(current-DIFFERENCE/2);
			}else{
				result += trade.getProfitLoss(current+DIFFERENCE/2);
			}
		}
		return result;
	}
	public List<Trade> getValidTradeList() {
		return tradeList.stream().filter(t -> !t.isSettlement()).collect(Collectors.toList());
	}
	public static void setBuyScore(int buyScore) {
		BUY_SCORE = buyScore;
	}
	public static void setBuyScoreT(int maxSellScoreT) {
		BUY_SCORE_T = maxSellScoreT;
	}
	public static void setSettleScore(int maxSettle) {
		SETTLE_SCORE = maxSettle;
	}
	public static void setSettleScoreT(int maxSettleT) {
		SETTLE_SCORE = maxSettleT;
	}
}
