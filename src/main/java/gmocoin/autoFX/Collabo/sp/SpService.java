package gmocoin.autoFX.Collabo.sp;

import java.text.*;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import gmocoin.autoFX.Collabo.IService;
import gmocoin.autoFX.Collabo.ISession;
import gmocoin.autoFX.control.Control;
import gmocoin.autoFX.strategy.PriceData;
import gmocoin.autoFX.strategy.StatisticsData;
import gmocoin.autoFX.strategy.Strategy;
import gmocoin.autoFX.strategy.Trade;

public class SpService implements IService,Runnable{
	private SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm",Locale.US);
    private ISession session;
    private StatisticsData data;
    private List<Strategy> strategyList;
    private static final int RETRY_TIMES=10;
    
    private boolean isTest=true;
    private Control control;

    public SpService(ISession session) {
        this.session = session;
        this.data = StatisticsData.getInstance();
        this.strategyList = new ArrayList<>();
        this.control = Control.getInstance();
        this.isTest = Boolean.valueOf(control.getProperty("isTest"));
    }

	@Override
	public void init() {
		// TODO Auto-generated method stub
		
	}
	
	public void calBestScore(){
		SpService self = this;
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				int[] results = optimisation(40,90);
			}
		});
		t.start();
	}
	
	private int[] optimisation(int sbuyScore,int ebuyScore) {
		List<PriceData> dataList=data.getDataList();

		int maxProfit = 0;
		int maxBuyScore = 1000;
		int maxSettleScore = 1000;
		int maxBuyScoreT = 1000;
		int maxSettleScoreT = 1000;
		int maxLostPersent = 0;
		
		int profit = 0;
		int buyScore = sbuyScore;
		int SettleScore = 35;
		int buyScoreT = 40;
		int settleScoreT = 40;
		int lostPersent = 0;
		for(;buyScore<ebuyScore;buyScore+=3){
			for(SettleScore = 35;SettleScore<90;SettleScore+=3){
				for(buyScoreT = buyScore+10;buyScoreT<110;buyScoreT+=4){
					for(settleScoreT = SettleScore+10;settleScoreT<110;settleScoreT+=4){
						StatisticsData testData = StatisticsData.getTestInstance();
						Strategy strategy = new Strategy(this,buyScore,SettleScore,buyScoreT,settleScoreT,testData);
						for(int i = 0;i<dataList.size();i++){
							PriceData data = dataList.get(i);
							testData.add(data);
							if(i>150){
								strategy.run();
							}
						}
						if(strategy.getSettledTradeCount() > 0){
							lostPersent = strategy.getLostCutTradeCount()*100/strategy.getSettledTradeCount();
						}else{
							lostPersent = 100;
						}
						profit = strategy.getProfitLoss();
						
						int score = profit - lostPersent * lostPersent * 30; 
						int maxScore = maxProfit - maxLostPersent * maxLostPersent * 30;
						
						if(score > maxScore){
							maxProfit = strategy.getProfitLoss();
							maxBuyScore = buyScore;
							maxLostPersent = lostPersent;
							maxSettleScore = SettleScore;
							maxBuyScoreT = buyScoreT;
							maxSettleScoreT = settleScoreT;
						}
							
					}
				}
			}
		}
		System.out.println("-----------------最適化-----------------------");
		System.out.println("maxProfit:"+maxProfit);
		System.out.println("maxBuyScore:"+maxBuyScore);
		System.out.println("maxSettleScore:"+maxSettleScore);
		System.out.println("maxBuyScoreT:"+maxBuyScoreT);
		System.out.println("maxSettleScoreT:"+maxSettleScoreT);
		System.out.println("maxLostPersent:"+maxLostPersent);
		System.out.println("---------------------------------------------");
		Strategy.setBuyScore(maxBuyScore);
		Strategy.setSettleScore(maxSettleScore);
		Strategy.setBuyScoreT(maxBuyScoreT);
		Strategy.setSettleScoreT(maxSettleScoreT);
		
		control.setProperty("buyScore", maxBuyScore + "");
		control.setProperty("settleScore", maxSettleScore + "");
		control.setProperty("buyScoreT", maxBuyScoreT + "");
		control.setProperty("settleScoreT", maxSettleScoreT + "");
		
		return new int[]{maxProfit,maxBuyScore,maxLostPersent,maxBuyScoreT};
	}

	@Override
	public void run() {
		
		Map<String,String> params1 = new HashMap<>();
		params1.put("productId", "10001");
		params1.put("multiBandType", "0");
		params1.put("bidAskType", "1");
		params1.put("chartType", "1");
		params1.put("size", "100000");
		StringBuffer html;
		JSONObject dataJson;
		try {
			html = this.session.sendGet("https://coin.z.com/api/v1/quote/getChart", params1);
			dataJson = new JSONObject(html.toString());
			JSONArray array = dataJson.getJSONArray("data");
			//加载策略
			Strategy strategy = new Strategy(this,this.isTest);
			strategyList.add(strategy);
			for (int i=0;i<array.length();i++){
				PriceData priceData = new PriceData((JSONObject) array.get(i));
				data.add(priceData);
				if(this.isTest)
					this.strategyList.stream().forEach(s -> s.run());
			}
			
			calBestScore();
			Thread.sleep(3000);
			
			int count = 0;
			
			while(true){
				if(count > 100000){
					calBestScore();
					Thread.sleep(1000);
					count = 0;
				}
				PriceData priceData = getCurrentData();
				if (priceData == null) {
					Thread.sleep(100);
					continue;
				}
				data.add(priceData);
				this.strategyList.stream().forEach(s -> s.run());
				count++;
				Thread.sleep(300);
			}
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	private PriceData getCurrentData() throws JSONException{
		StringBuffer html;
		Map<String,String> params = new HashMap<>();
		params.put("productId", "10001");
		params.put("multiBandType", "0");
		params.put("bidAskType", "1");
		params.put("chartType", "1");
		Calendar calendar = Calendar.getInstance();
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		params.put("datetime", sdf.format(calendar.getTime()));
		PriceData priceData = null;
		int times = 0;
		while(priceData == null && times <= RETRY_TIMES){
			html = this.session.sendGet("https://coin.z.com/api/v1/quote/getChart", params);
			JSONObject dataJson = new JSONObject(html.toString());
			JSONArray array = dataJson.getJSONArray("data");
			if(dataJson.getInt("status") == 0 && array.length() > 0){
				priceData = new PriceData((JSONObject) array.get(0));
				return priceData;
			}
			times++;
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public StatisticsData getData(){
		return this.data;
	}
	
	public int getProfitLoss() {
		int result = 0;
		for (Strategy strategy:strategyList){
			result += strategy.getProfitLoss();
		}
		return result;
	}
	
	public int getVaildProfitLoss() {
		int result = 0;
		for (Strategy strategy:strategyList){
			result += strategy.getVaildProfitLoss();
		}
		return result;
	}
	
	public Trade newTread(boolean isBuy, float quantit){
		JSONObject param = new JSONObject();
		try {
			PriceData priceData = getCurrentData();
			int price = 0;
			if(isBuy){
				price = priceData.closePrice + 1500/2 +1;
			}else{
				price = priceData.closePrice - 1500/2 -1;
			}
			param.put("buySellType", isBuy?1:2);
			param.put("multiBandType", 0);
			param.put("orderQuantity", String.valueOf(quantit));
			param.put("orderRate", price + "");
			param.put("productId", 10001);
			String res = session.sendPost(SpComConstants.MAKE_ORDER_URL, param).toString();
			JSONObject JsonRes = new JSONObject(res);
			if(JsonRes.getInt("status") == 0){
				return new Trade(isBuy, priceData, JsonRes.getJSONObject("data").getInt("executionRate"), quantit);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public boolean settleTread(Trade trade){
		try {
			PriceData priceData = getCurrentData();
			if (priceData == null){
				return false;
			}
			int price = 0;
			if(trade.isBuy()){
				price = priceData.closePrice - 1500/2 -1;
			}else{
				price = priceData.closePrice + 1500/2 +1;
			}
			String res = session.sendGet(SpComConstants.POSITIONS_URL, new HashMap<>()).toString();
			JSONObject JsonRes;
			JsonRes = new JSONObject(res);
			if(JsonRes.getInt("status") == 0){
				JSONArray tradeList = JsonRes.getJSONObject("data").getJSONArray("list");
				for (int i=0;i<tradeList.length();i++){
					JSONObject tradeJson = tradeList.getJSONObject(i);
					if(tradeJson.getInt("positionRate") == trade.getPrice() 
							&& tradeJson.getString("positionQuantity").contains(String.valueOf(trade.getQuantity()))
							&& ((tradeJson.getInt("buySellType") == 1)==trade.isBuy())){
						JSONObject param = new JSONObject();
						JSONArray positionArray = new JSONArray();
						JSONObject positionJson = new JSONObject();
						positionArray.put(positionJson);
						positionJson.put("orderQuantity", tradeJson.getString("positionQuantity"));
						positionJson.put("positionId", tradeJson.getLong("positionId"));
						param.put("buySellType", trade.isBuy()?2:1);
						param.put("multiBandType", 0);
						param.put("orderRate", price + "");
						param.put("productId", 10001);
						param.put("settlePosition", (Object)positionArray);
						String res1 = session.sendPost(SpComConstants.SETTLE_URL, param).toString();
						JSONObject JsonRes1 = new JSONObject(res1);
						if(JsonRes1.getInt("status") == 0){
							trade.doSettlement(JsonRes1.getJSONObject("data").getInt("executionRate"));
							return true;
						}
						break;
					}
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}
	
	public int getValidTradeCount(){
		int count = 0;
		for(Strategy s : this.strategyList){
			count += s.getValidTradeCount();
		}
		return count;
	}
	public int getSettledTradeCount(){
		int count = 0;
		for(Strategy s : this.strategyList){
			count += s.getSettledTradeCount();
		}
		return count;
	}

	public List<Trade> getValidTradeList() {
		List<Trade> result = new ArrayList<>();
		for(Strategy s : this.strategyList){
			result.addAll(s.getValidTradeList());
		}
		return result;
	}

}
