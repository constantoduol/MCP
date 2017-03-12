var kinds, filters, orders, limits, run, onFinish, aggregate, beforeNextData;
var verbose = false;
var ECHO_URL = "https://constant4-dot-m-swali-hrd.appspot.com/api/cms/mcp?";

function nextData() {
    if(!run) return;
    var next = true;
    if(beforeNextData)
        next = beforeNextData();
    if(!next) {
        //kill the process and return
        kill_script();
        return;
    }
    var params = "request_id="+_request_id_+"&";
    if (kinds)
        params += "kinds=" + encodeURIComponent(JSON.stringify(kinds)) + "&";
    if (filters)
        params += "filters=" + encodeURIComponent(JSON.stringify(filters)) + "&";
    if (orders)
        params += "orders=" + encodeURIComponent(JSON.stringify(orders)) + "&";
    if (limits)
        params += "limits=" + encodeURIComponent(JSON.stringify(limits));
    
    var result = _task_.post(ECHO_URL, params, "nextdata");
    if(verbose) _task_.sendMessage(result);
    
    //check if we received any valid data
    _data_ = {};
    if(result) _data_ = JSON.parse(result).results;
    if(verbose) _task_.sendMessage(JSON.stringify(_data_));
    //perform run if there is data, otherwise send a kill message
    //to notify that there is no more data to process and call on finish
    if(hasData(_data_)) 
        run();
    else
        kill_script();
    
}

function kill_script(){
    var params = "svc=mcp_service&msg=kill_script&request_id="+_request_id_+"&"+"&script="+_script_;
    _task_.post(_self_url_, params, "kill_script");
}

function hasData(obj){
    var dataFound = false;
    for (var key in obj) {
        if (obj[key].length > 0) {
            dataFound = true;
            break;
        }
    }
    return dataFound;
}

function exit(resp) {
    //get aggregator and send the data
    var postUrl = _self_url_ + "?";
    resp = encodeURIComponent(JSON.stringify(resp));
    var params = "svc=mcp_service&msg=aggregate&request_id="+_request_id_+"&"+"&response="+resp+"&script="+_script_;
    var result = _task_.post(postUrl, params, "aggregate");
    _self_state_ = {};
    if(verbose) _task_.sendMessage(result);
    if(result) _self_state_ = JSON.parse(result).response.data;
    if(_self_state_.kill_process) {
        _task_.sendMessage("process killed prematurely, exiting");
        return; //process was killed no need to continue;
    }
    nextData();
}

//this helps to perform a query within run
function query(kinds, filters, orders, limits, requestId, onData){
    //kinds, filters, orders, limits
    if(!requestId) requestId = Math.floor(Math.random()*10000000000);
    var params = "request_id=" + requestId + "&";
    if (kinds)
        params += "kinds=" + encodeURIComponent(JSON.stringify(kinds)) + "&";
    if (filters)
        params += "filters=" + encodeURIComponent(JSON.stringify(filters)) + "&";
    if (orders)
        params += "orders=" + encodeURIComponent(JSON.stringify(orders)) + "&";
    if (limits)
        params += "limits=" + encodeURIComponent(JSON.stringify(limits));
    var result = _task_.post(ECHO_URL, params, "query");
    if(result) result = JSON.parse(result).results;
    if(hasData(result)){
         if(onData) onData(result);
         query(kinds, filters, orders, limits, requestId, onData); //recursively go through all the results
    }
}

