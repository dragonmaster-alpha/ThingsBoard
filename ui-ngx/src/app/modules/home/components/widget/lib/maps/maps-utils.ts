///
/// Copyright © 2016-2020 The Thingsboard Authors
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

import L from 'leaflet';
import _ from 'lodash';
import { MarkerSettings, PolylineSettings, PolygonSettings } from './map-models';

export function createTooltip(target: L.Layer,
    settings: MarkerSettings | PolylineSettings | PolygonSettings,
    content?: string | HTMLElement): L.Popup {
    const popup = L.popup();
    popup.setContent(content);
    console.log(settings);

    target.bindPopup(popup, { autoClose: settings.autocloseTooltip, closeOnClick: false });
    target.on('popupopen', () => {
        let actions = document.getElementsByClassName('tb-custom-action');
        Array.from(actions).forEach(
            (element: HTMLElement) => {
                if (element && settings.tooltipActions[element.id]) {
                    console.log(settings.tooltipActions[element.id]);
                    element.addEventListener('click', settings.tooltipActions[element.id])
                }
            })
    })
    if (settings.displayTooltipAction === 'hover') {
        target.off('click');
        target.on('mouseover', function () {
            this.openPopup();
        });
        target.on('mouseout', function () {
            this.closePopup();
        });
    }
    return popup;
}

export function getRatio(firsMoment: number, secondMoment: number, intermediateMoment: number): number {
    return (intermediateMoment - firsMoment) / (secondMoment - firsMoment);
};

export function findAngle(startPoint, endPoint) {
    let angle = -Math.atan2(endPoint.latitude - startPoint.latitude, endPoint.longitude - startPoint.longitude);
    angle = angle * 180 / Math.PI;
    return parseInt(angle.toFixed(2), 10);
}


export function getDefCenterPosition(position) {
    if (typeof (position) === 'string')
        return position.split(',');
    if (typeof (position) === 'object')
        return position;
    return [0, 0];
}