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
import LeafletMap from '../leaflet-map';
import { MapOptions } from '../map-models';

export class OpenStreetMap extends LeafletMap {
    constructor($container, options: MapOptions) {
        super($container, options);
        const map = L.map($container).setView(options?.defaultCenterPosition, options?.defaultZoomLevel);
        var tileLayer = (L.tileLayer as any).provider("OpenStreetMap.Mapnik");
        tileLayer.addTo(map);
        super.setMap(map);
        super.initSettings(options);
    }
}