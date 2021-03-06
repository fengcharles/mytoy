package cc.fyp.toy.service.evcard;

import cc.fyp.toy.controller.EvcardController;
import cc.fyp.toy.service.evcard.dto.*;
import cc.fyp.toy.service.evcard.outapi.EvcardApi;
import cc.fyp.toy.service.mesg.DingTalkMesg;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EvcardService{

    private final static Logger logger = LoggerFactory.getLogger(EvcardController.class);

    @Autowired
    private TaskExecutor taskExecutor;

    @Value("${card.reqHost}")
    private String reqHost;

    @Value("${card.arae}")
    private String options;

    public static final List<QueryTask> QUERYS = new ArrayList<>();

    public void loadCard(QueryDTO queryParam){

        List<AreaResq> areaResqList =  JSONArray.parseArray(options,AreaResq.class);
        queryParam.getAreas().forEach(ap->{
            QueryTask query = new QueryTask();
            query.setOption(ap);
            query.setTypes(queryParam.getTypes());
            query.setStartDate(queryParam.getStartDate());
            query.setQueryTime(queryParam.getQueryTime());
            query.setOil(queryParam.getOil());
            query.setQuerySeq(queryParam.getQuerySeq());
            String name = areaResqList.stream().filter(a -> a.getId().equals(ap)).map(AreaResq::getName).findFirst().get();
            query.setSeqName(name);
            query.setHost(reqHost);
            query.setFlag(true);
            query.setTaskId(System.currentTimeMillis()+ap);
            EvcardQueryJob evcardQueryJob = new EvcardQueryJob();
            evcardQueryJob.setQueryTask(query);
            taskExecutor.execute(evcardQueryJob);
            QUERYS.add(query);
        });
    }


    /**
     * 从某个停车点获取符合条件的车辆
     * @param option
     * @return
     */
    public  FindCarResultDTO findUseCar(QueryTask option){
        EvcardApi evcardApi = new EvcardApi();
        QueryCardReqst reqst = new QueryCardReqst();
        reqst.setCanRent("1");
        reqst.setShopSeq(Long.valueOf(option.getOption()));
        EvcardComm evcardComm =  evcardApi.findCards(reqst,getHeader());
        if (evcardComm.getStatus() != 0){
            return  new FindCarResultDTO(false,evcardComm.getMessage());
        }
        List<Evcards> list =  evcardComm.loadDataList(Evcards.class);

        StringBuffer sb = new StringBuffer();
        sb.append("查询到了符合的车辆");
        sb.append("\n");
        sb.append("=================");

        List<Evcards> canUseList = new ArrayList<>();
        for (Evcards evcard : list) {
            Boolean flag = option.getTypes().contains(evcard.getVehicleModelName());
            Integer soc = Integer.valueOf(evcard.getDrivingRange());
            if ( flag && soc >= option.getOil()){
                canUseList.add(evcard);
                logger.info("查询到符合的结果：{}",evcard);
            }
        }

        if (canUseList.size()==0){
            return  new FindCarResultDTO(false,"无车");
        }
        this.sortCardList(canUseList,sb,option);
        DingTalkMesg.callMe(sb.toString());
        QUERYS.remove(option);
        return  new FindCarResultDTO(true,"已查到");
    }


    private  void sortCardList(List<Evcards> canUseList, StringBuffer sb, QueryTask option){
        canUseList = canUseList.stream()
                .sorted((o,n) -> n.getDrivingRange().compareTo(o.getDrivingRange())).collect(Collectors.toList());

        canUseList.forEach(evcard -> {
            sb.append("\n");
            sb.append("车辆名称："+evcard.getVehicleModelName());
            sb.append("\n");
            sb.append("车牌号：" + evcard.getVehicleNo());
            sb.append("\n");
            sb.append("可行驶距离："+evcard.getDrivingRange());
            sb.append("\n");
            sb.append("vin："+evcard.getVin());
            sb.append("\n");
            sb.append("位置："+evcard.getShopName());
            sb.append("\n");
            sb.append("点击链接，即可预约：\n"+  option.getHost() + "/mytoy/card/order?seq="+evcard.getShopSeq()+"&vin="+evcard.getVin());
            sb.append("\n");
            sb.append("=================");
        });
        logger.info(canUseList.toString());
    }

    public EvcardComm order(String seq,String vin){
        EvcardApi evcardApi = new EvcardApi();
        EvcardComm resp =  evcardApi.order(getHeader(),seq,vin);
        return resp;
    }


    public void saveHeader(String header){
        logger.info("保存头部信息到header");
        try {

            File file = new File("/home/von/opt/evcardHeader");
            File dri = file.getParentFile();

            if (!dri.exists()){
                dri.mkdirs();
            }

            if (!file.exists()){
                file.createNewFile();
            }

            try (FileWriter fileWriter = new FileWriter(file)){
                fileWriter.write(header);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public EvcardHeader getHeader() {

        File file = new File("/home/von/opt/evcardHeader");
        try (FileReader fileReader = new FileReader(file)) {
            BufferedReader reader = new BufferedReader(fileReader);
            String  header = reader.readLine();
            logger.info("获取到文件头部信息:" + header);
            return JSONObject.parseObject(header).toJavaObject(EvcardHeader.class);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

}
