/*
 * Copyright © 2016-2017 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
export default angular.module('thingsboard.api.alarm', [])
    .factory('alarmService', AlarmService)
    .name;

/*@ngInject*/
function AlarmService($http, $q, $interval, $filter, $timeout, utils, types) {

    var alarmSourceListeners = {};

    var simulatedAlarm = {
        createdTime: (new Date).getTime(),
        startTs: (new Date).getTime(),
        endTs: 0,
        ackTs: 0,
        clearTs: 0,
        originatorName: 'Simulated',
        originator: {
            entityType: "DEVICE",
            id: "1"
        },
        type: 'TEMPERATURE',
        severity: "MAJOR",
        status: types.alarmStatus.activeUnack,
        details: {
            message: "Temperature is high!"
        }
    };

    var service = {
        getAlarm: getAlarm,
        getAlarmInfo: getAlarmInfo,
        saveAlarm: saveAlarm,
        ackAlarm: ackAlarm,
        clearAlarm: clearAlarm,
        getAlarms: getAlarms,
        pollAlarms: pollAlarms,
        cancelPollAlarms: cancelPollAlarms,
        subscribeForAlarms: subscribeForAlarms,
        unsubscribeFromAlarms: unsubscribeFromAlarms
    }

    return service;

    function getAlarm(alarmId, ignoreErrors, config) {
        var deferred = $q.defer();
        var url = '/api/alarm/' + alarmId;
        if (!config) {
            config = {};
        }
        config = Object.assign(config, { ignoreErrors: ignoreErrors });
        $http.get(url, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function getAlarmInfo(alarmId, ignoreErrors, config) {
        var deferred = $q.defer();
        var url = '/api/alarm/info/' + alarmId;
        if (!config) {
            config = {};
        }
        config = Object.assign(config, { ignoreErrors: ignoreErrors });
        $http.get(url, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function saveAlarm(alarm, ignoreErrors, config) {
        var deferred = $q.defer();
        var url = '/api/alarm';
        if (!config) {
            config = {};
        }
        config = Object.assign(config, { ignoreErrors: ignoreErrors });
        $http.post(url, alarm, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function ackAlarm(alarmId, ignoreErrors, config) {
        var deferred = $q.defer();
        var url = '/api/alarm/' + alarmId + '/ack';
        if (!config) {
            config = {};
        }
        config = Object.assign(config, { ignoreErrors: ignoreErrors });
        $http.post(url, null, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function clearAlarm(alarmId, ignoreErrors, config) {
        var deferred = $q.defer();
        var url = '/api/alarm/' + alarmId + '/clear';
        if (!config) {
            config = {};
        }
        config = Object.assign(config, { ignoreErrors: ignoreErrors });
        $http.post(url, null, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function getAlarms(entityType, entityId, pageLink, alarmSearchStatus, alarmStatus, fetchOriginator, ascOrder, config) {
        var deferred = $q.defer();
        var url = '/api/alarm/' + entityType + '/' + entityId + '?limit=' + pageLink.limit;

        if (angular.isDefined(pageLink.startTime)) {
            url += '&startTime=' + pageLink.startTime;
        }
        if (angular.isDefined(pageLink.endTime)) {
            url += '&endTime=' + pageLink.endTime;
        }
        if (angular.isDefined(pageLink.idOffset)) {
            url += '&offset=' + pageLink.idOffset;
        }
        if (alarmSearchStatus) {
            url += '&searchStatus=' + alarmSearchStatus;
        }
        if (alarmStatus) {
            url += '&status=' + alarmStatus;
        }
        if (fetchOriginator) {
            url += '&fetchOriginator=' + ((fetchOriginator===true) ? 'true' : 'false');
        }
        if (angular.isDefined(ascOrder) && ascOrder != null) {
            url += '&ascOrder=' + (ascOrder ? 'true' : 'false');
        }

        $http.get(url, config).then(function success(response) {
            deferred.resolve(response.data);
        }, function fail() {
            deferred.reject();
        });
        return deferred.promise;
    }

    function fetchAlarms(alarmsQuery, pageLink, deferred, alarmsList) {
        getAlarms(alarmsQuery.entityType, alarmsQuery.entityId,
            pageLink, alarmsQuery.alarmSearchStatus, alarmsQuery.alarmStatus,
            alarmsQuery.fetchOriginator, false, {ignoreLoading: true}).then(
            function success(alarms) {
                if (!alarmsList) {
                    alarmsList = [];
                }
                alarmsList = alarmsList.concat(alarms.data);
                if (alarms.hasNext && !alarmsQuery.limit) {
                    fetchAlarms(alarmsQuery, alarms.nextPageLink, deferred, alarmsList);
                } else {
                    alarmsList = $filter('orderBy')(alarmsList, ['-createdTime']);
                    deferred.resolve(alarmsList);
                }
            },
            function fail() {
                deferred.reject();
            }
        );
    }

    function getAlarmsByQuery(alarmsQuery) {
        var deferred = $q.defer();
        var time = Date.now();
        var pageLink;
        if (alarmsQuery.limit) {
            pageLink = {
                limit: alarmsQuery.limit
            };
        } else if (alarmsQuery.interval) {
            pageLink = {
                limit: 100,
                startTime: time - alarmsQuery.interval
            };
        } else if (alarmsQuery.startTime) {
            pageLink = {
                limit: 100,
                startTime: alarmsQuery.startTime
            }
            if (alarmsQuery.endTime) {
                pageLink.endTime = alarmsQuery.endTime;
            }
        }

        fetchAlarms(alarmsQuery, pageLink, deferred);
        return deferred.promise;
    }

    function onPollAlarms(alarmsQuery) {
        getAlarmsByQuery(alarmsQuery).then(
            function success(alarms) {
                alarmsQuery.onAlarms(alarms);
            },
            function fail() {}
        );
    }

    function pollAlarms(entityType, entityId, alarmStatus, interval, limit, pollingInterval, onAlarms) {
        var alarmsQuery = {
            entityType: entityType,
            entityId: entityId,
            alarmSearchStatus: null,
            alarmStatus: alarmStatus,
            fetchOriginator: false,
            interval: interval,
            limit: limit,
            onAlarms: onAlarms
        };
        onPollAlarms(alarmsQuery);
        return $interval(onPollAlarms, pollingInterval, 0, false, alarmsQuery);
    }

    function cancelPollAlarms(pollPromise) {
        if (angular.isDefined(pollPromise)) {
            $interval.cancel(pollPromise);
        }
    }

    function subscribeForAlarms(alarmSourceListener) {
        alarmSourceListener.id = utils.guid();
        alarmSourceListeners[alarmSourceListener.id] = alarmSourceListener;
        var alarmSource = alarmSourceListener.alarmSource;
        if (alarmSource.type == types.datasourceType.function) {
            $timeout(function() {
                alarmSourceListener.alarmsUpdated([simulatedAlarm], false);
            });
        } else {
            var pollingInterval = 5000; //TODO:
            alarmSourceListener.alarmsQuery = {
                entityType: alarmSource.entityType,
                entityId: alarmSource.entityId,
                alarmSearchStatus: null, //TODO:
                alarmStatus: null
            }
            var originatorKeys = $filter('filter')(alarmSource.dataKeys, {name: 'originator'});
            if (originatorKeys && originatorKeys.length) {
                alarmSourceListener.alarmsQuery.fetchOriginator = true;
            }
            var subscriptionTimewindow = alarmSourceListener.subscriptionTimewindow;
            if (subscriptionTimewindow.realtimeWindowMs) {
                alarmSourceListener.alarmsQuery.startTime = subscriptionTimewindow.startTs;
            } else {
                alarmSourceListener.alarmsQuery.startTime = subscriptionTimewindow.fixedWindow.startTimeMs;
                alarmSourceListener.alarmsQuery.endTime = subscriptionTimewindow.fixedWindow.endTimeMs;
            }
            alarmSourceListener.alarmsQuery.onAlarms = function(alarms) {
                if (subscriptionTimewindow.realtimeWindowMs) {
                    var now = Date.now();
                    if (alarmSourceListener.lastUpdateTs) {
                        var interval = now - alarmSourceListener.lastUpdateTs;
                        alarmSourceListener.alarmsQuery.startTime += interval;
                    } else {
                        alarmSourceListener.lastUpdateTs = now;
                    }
                }
                alarmSourceListener.alarmsUpdated(alarms, false);
            }
            onPollAlarms(alarmSourceListener.alarmsQuery);
            alarmSourceListener.pollPromise = $interval(onPollAlarms, pollingInterval,
                0, false, alarmSourceListener.alarmsQuery);
        }

    }

    function unsubscribeFromAlarms(alarmSourceListener) {
        if (alarmSourceListener && alarmSourceListener.id) {
            if (alarmSourceListener.pollPromise) {
                $interval.cancel(alarmSourceListener.pollPromise);
                alarmSourceListener.pollPromise = null;
            }
            delete alarmSourceListeners[alarmSourceListener.id];
        }
    }
}
