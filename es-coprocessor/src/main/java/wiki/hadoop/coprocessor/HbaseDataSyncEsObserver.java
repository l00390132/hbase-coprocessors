package wiki.hadoop.coprocessor;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Durability;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessor;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.coprocessor.RegionObserver;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.wal.WALEdit;
import org.apache.log4j.Logger;

import wiki.hadoop.es.ESClient;
import wiki.hadoop.es.ElasticSearchBulkOperator;

import java.io.IOException;
import java.util.*;

/**
 * Created on 2019/1/10.
 *
 * https://www.codercto.com/a/57019.html
 */
/**
 * PUT user_test
{
  "settings": {
    "number_of_replicas": 1
    , "number_of_shards": 5
  }
}


PUT user_test/_mapping/user_test_type
{
  
  "user_test_type":{
    "properties":{
      "log_code":{"type":"text"},
      "sys_code":{"type":"text"},
      "biz_type":{"type":"text"},
      "log_level":{"type":"text"},
      "log_time":{"type":   "date",
  "format": "yyy年MM月dd日 HH时mm分ss秒"},
      "begin_time":{"type":   "date",
  "format": "yyy年MM月dd日 HH时mm分ss秒"},
      "end_time":{"type":   "date",
  "format": "yyy年MM月dd日 HH时mm分ss秒"},
      "client_ip":{"type":"text"},
      "server_ip":{"type":"text"},
      "log_msg":{"type":"text"},
      "use_time":{"type":"long"},
      "request_length":{"type":"long"},
      "response_length":{"type":"long"},
      "log_status":{"type":"short"},
      "request_url":{"type":"text"},
      "directory":{"type":"text"},
      "download_type":{"type":"text"},
      "task_keyword":{"type":"text","fielddata": true},
      "kafka_topic":{"type":"text"},
      "kafka_group":{"type":"text"},
      "crawler_type":{"type":"text"},
      "json":{"type":"text"}
    }
  }
}
 * @author jast
 * @date 2020年8月11日 下午5:10:05
 */
public class HbaseDataSyncEsObserver implements RegionObserver , RegionCoprocessor {

    private static final Logger LOG = Logger.getLogger(HbaseDataSyncEsObserver.class);

    public Optional<RegionObserver> getRegionObserver() {
        return Optional.of(this);
    }

    @Override
    public void start(CoprocessorEnvironment env) throws IOException {
        // init ES client
        ESClient.initEsClient();
        LOG.info("****init start*****");
    }

    @Override
    public void stop(CoprocessorEnvironment env) throws IOException {
        ESClient.closeEsClient();
        // shutdown time task
        ElasticSearchBulkOperator.shutdownScheduEx();
        LOG.info("****end*****");
    }

    @Override
    public void postPut(ObserverContext<RegionCoprocessorEnvironment> e, Put put, WALEdit edit, Durability durability) throws IOException {
        String indexId = new String(put.getRow());
        try {
            NavigableMap<byte[], List<Cell>> familyMap = put.getFamilyCellMap();
            Map<String, Object> infoJson = new HashMap<>();
            Map<String, Object> json = new HashMap<>();
            for (Map.Entry<byte[], List<Cell>> entry : familyMap.entrySet()) {
                for (Cell cell : entry.getValue()) {
                    String key = Bytes.toString(CellUtil.cloneQualifier(cell));
                    String value = Bytes.toString(CellUtil.cloneValue(cell));
                    json.put(key, value);
                }
            }
            // set hbase family to es
            infoJson.put("info", json);
            LOG.info(json.toString());
            ElasticSearchBulkOperator.addUpdateBuilderToBulk(ESClient.client.prepareUpdate("user_test","user_test_type", indexId).setDocAsUpsert(true).setDoc(json));
            LOG.info("**** postPut success*****");
        } catch (Exception ex) {
            LOG.error("observer put  a doc, index [ " + "user_test" + " ]" + "indexId [" + indexId + "] error : " + ex.getMessage());
        }
    }
    @Override
    public void postDelete(ObserverContext<RegionCoprocessorEnvironment> e, Delete delete, WALEdit edit, Durability durability) throws IOException {
        String indexId = new String(delete.getRow());
        try {
            ElasticSearchBulkOperator.addDeleteBuilderToBulk(ESClient.client.prepareDelete("user_test", "user_test_type", indexId));
            LOG.info("**** postDelete success*****");
        } catch (Exception ex) {
            LOG.error(ex);
            LOG.error("observer delete  a doc, index [ " + "user_test" + " ]" + "indexId [" + indexId + "] error : " + ex.getMessage());

        }
    }
}