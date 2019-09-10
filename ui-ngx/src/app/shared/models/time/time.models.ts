///
/// Copyright © 2016-2019 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { TimeService } from '@core/services/time.service';
import { deepClone, isDefined } from '@app/core/utils';

export const SECOND = 1000;
export const MINUTE = 60 * SECOND;
export const HOUR = 60 * MINUTE;
export const DAY = 24 * HOUR;

export enum TimewindowType {
  REALTIME,
  HISTORY
}

export enum HistoryWindowType {
  LAST_INTERVAL,
  FIXED
}

export interface IntervalWindow {
  interval?: number;
  timewindowMs?: number;
}

export interface FixedWindow {
  startTimeMs: number;
  endTimeMs: number;
}

export interface HistoryWindow extends IntervalWindow {
  historyType?: HistoryWindowType;
  fixedTimewindow?: FixedWindow;
}

export enum AggregationType {
  MIN = 'MIN',
  MAX = 'MAX',
  AVG = 'AVG',
  SUM = 'SUM',
  COUNT = 'COUNT',
  NONE = 'NONE'
}

export const aggregationTranslations = new Map<AggregationType, string>(
  [
    [AggregationType.MIN, 'aggregation.min'],
    [AggregationType.MAX, 'aggregation.max'],
    [AggregationType.AVG, 'aggregation.avg'],
    [AggregationType.SUM, 'aggregation.sum'],
    [AggregationType.COUNT, 'aggregation.count'],
    [AggregationType.NONE, 'aggregation.none'],
  ]
);

export interface Aggregation {
  type: AggregationType;
  limit: number;
}

export interface Timewindow {
  displayValue?: string;
  selectedTab?: TimewindowType;
  realtime?: IntervalWindow;
  history?: HistoryWindow;
  aggregation?: Aggregation;
  stDiff?: number;
}

export function historyInterval(timewindowMs: number): Timewindow {
  const timewindow: Timewindow = {
    history: {
      timewindowMs
    }
  };
  return timewindow;
}

export function defaultTimewindow(timeService: TimeService): Timewindow {
  const currentTime = new Date().getTime();
  const timewindow: Timewindow = {
    displayValue: '',
    selectedTab: TimewindowType.REALTIME,
    realtime: {
      interval: SECOND,
      timewindowMs: MINUTE
    },
    history: {
      historyType: HistoryWindowType.LAST_INTERVAL,
      interval: SECOND,
      timewindowMs: MINUTE,
      fixedTimewindow: {
        startTimeMs: currentTime - DAY,
        endTimeMs: currentTime
      }
    },
    aggregation: {
      type: AggregationType.AVG,
      limit: Math.floor(timeService.getMaxDatapointsLimit() / 2)
    }
  };
  return timewindow;
}

export function initModelFromDefaultTimewindow(value: Timewindow, timeService: TimeService): Timewindow {
  const model = defaultTimewindow(timeService);
  if (value) {
    if (value.realtime) {
      model.selectedTab = TimewindowType.REALTIME;
      if (isDefined(value.realtime.interval)) {
        model.realtime.interval = value.realtime.interval;
      }
      model.realtime.timewindowMs = value.realtime.timewindowMs;
    } else {
      model.selectedTab = TimewindowType.HISTORY;
      if (isDefined(value.history.interval)) {
        model.history.interval = value.history.interval;
      }
      if (isDefined(value.history.timewindowMs)) {
        model.history.historyType = HistoryWindowType.LAST_INTERVAL;
        model.history.timewindowMs = value.history.timewindowMs;
      } else {
        model.history.historyType = HistoryWindowType.FIXED;
        model.history.fixedTimewindow.startTimeMs = value.history.fixedTimewindow.startTimeMs;
        model.history.fixedTimewindow.endTimeMs = value.history.fixedTimewindow.endTimeMs;
      }
    }
    if (value.aggregation) {
      if (value.aggregation.type) {
        model.aggregation.type = value.aggregation.type;
      }
      model.aggregation.limit = value.aggregation.limit || Math.floor(timeService.getMaxDatapointsLimit() / 2);
    }
  }
  return model;
}

export function toHistoryTimewindow(timewindow: Timewindow, startTimeMs: number, endTimeMs: number,
                                    interval: number, timeService: TimeService): Timewindow {
  if (timewindow.history) {
    interval = isDefined(interval) ? interval : timewindow.history.interval;
  } else if (timewindow.realtime) {
    interval = timewindow.realtime.interval;
  }  else {
    interval = 0;
  }
  let aggType: AggregationType;
  let limit: number;
  if (timewindow.aggregation) {
    aggType = timewindow.aggregation.type || AggregationType.AVG;
    limit = timewindow.aggregation.limit || timeService.getMaxDatapointsLimit();
  } else {
    aggType = AggregationType.AVG;
    limit = timeService.getMaxDatapointsLimit();
  }
  const historyTimewindow: Timewindow = {
    history: {
      fixedTimewindow: {
        startTimeMs,
        endTimeMs
      },
      interval: timeService.boundIntervalToTimewindow(endTimeMs - startTimeMs, interval, AggregationType.AVG)
    },
    aggregation: {
      type: aggType,
      limit
    }
  };
  return historyTimewindow;
}

export function cloneSelectedTimewindow(timewindow: Timewindow): Timewindow {
  const cloned: Timewindow = {};
  if (isDefined(timewindow.selectedTab)) {
    cloned.selectedTab = timewindow.selectedTab;
    if (timewindow.selectedTab === TimewindowType.REALTIME) {
      cloned.realtime = deepClone(timewindow.realtime);
    } else if (timewindow.selectedTab === TimewindowType.HISTORY) {
      cloned.history = deepClone(timewindow.history);
    }
  }
  cloned.aggregation = deepClone(timewindow.aggregation);
  return cloned;
}

export function cloneSelectedHistoryTimewindow(historyWindow: HistoryWindow): HistoryWindow {
  const cloned: HistoryWindow = {};
  if (isDefined(historyWindow.historyType)) {
    cloned.historyType = historyWindow.historyType;
    cloned.interval = historyWindow.interval;
    if (historyWindow.historyType === HistoryWindowType.LAST_INTERVAL) {
      cloned.timewindowMs = historyWindow.timewindowMs;
    } else if (historyWindow.historyType === HistoryWindowType.FIXED) {
      cloned.fixedTimewindow = deepClone(historyWindow.fixedTimewindow);
    }
  }
  return cloned;
}

export interface TimeInterval {
  name: string;
  translateParams: {[key: string]: any};
  value: number;
}

export const defaultTimeIntervals = new Array<TimeInterval>(
  {
    name: 'timeinterval.seconds-interval',
    translateParams: {seconds: 1},
    value: 1 * SECOND
  },
  {
    name: 'timeinterval.seconds-interval',
    translateParams: {seconds: 5},
    value: 5 * SECOND
  },
  {
    name: 'timeinterval.seconds-interval',
    translateParams: {seconds: 10},
    value: 10 * SECOND
  },
  {
    name: 'timeinterval.seconds-interval',
    translateParams: {seconds: 15},
    value: 15 * SECOND
  },
  {
    name: 'timeinterval.seconds-interval',
    translateParams: {seconds: 30},
    value: 30 * SECOND
  },
  {
    name: 'timeinterval.minutes-interval',
    translateParams: {minutes: 1},
    value: 1 * MINUTE
  },
  {
    name: 'timeinterval.minutes-interval',
    translateParams: {minutes: 2},
    value: 2 * MINUTE
  },
  {
    name: 'timeinterval.minutes-interval',
    translateParams: {minutes: 5},
    value: 5 * MINUTE
  },
  {
    name: 'timeinterval.minutes-interval',
    translateParams: {minutes: 10},
    value: 10 * MINUTE
  },
  {
    name: 'timeinterval.minutes-interval',
    translateParams: {minutes: 15},
    value: 15 * MINUTE
  },
  {
    name: 'timeinterval.minutes-interval',
    translateParams: {minutes: 30},
    value: 30 * MINUTE
  },
  {
    name: 'timeinterval.hours-interval',
    translateParams: {hours: 1},
    value: 1 * HOUR
  },
  {
    name: 'timeinterval.hours-interval',
    translateParams: {hours: 2},
    value: 2 * HOUR
  },
  {
    name: 'timeinterval.hours-interval',
    translateParams: {hours: 5},
    value: 5 * HOUR
  },
  {
    name: 'timeinterval.hours-interval',
    translateParams: {hours: 10},
    value: 10 * HOUR
  },
  {
    name: 'timeinterval.hours-interval',
    translateParams: {hours: 12},
    value: 12 * HOUR
  },
  {
    name: 'timeinterval.days-interval',
    translateParams: {days: 1},
    value: 1 * DAY
  },
  {
    name: 'timeinterval.days-interval',
    translateParams: {days: 7},
    value: 7 * DAY
  },
  {
    name: 'timeinterval.days-interval',
    translateParams: {days: 30},
    value: 30 * DAY
  }
);
