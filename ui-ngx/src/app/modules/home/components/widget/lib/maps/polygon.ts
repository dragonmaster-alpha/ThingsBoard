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

import L, { LatLngExpression, LatLngTuple } from 'leaflet';
import { createTooltip } from './maps-utils';
import { PolygonSettings, FormattedData } from './map-models';
import { DatasourceData } from '@app/shared/models/widget.models';
import { safeExecute, parseWithTranslation } from '@app/core/utils';

export class Polygon {

    leafletPoly: L.Polygon;
    tooltip;
    data;
    dataSources;

    constructor(public map, polyData: DatasourceData, dataSources, private settings: PolygonSettings, onClickListener?) {
        this.leafletPoly = L.polygon(polyData.data, {
            fill: true,
            fillColor: settings.polygonColor,
            color: settings.polygonStrokeColor,
            weight: settings.polygonStrokeWeight,
            fillOpacity: settings.polygonOpacity,
            opacity: settings.polygonStrokeOpacity
        }).addTo(this.map);
        this.dataSources = dataSources;
        this.data = polyData;
        if (settings.showPolygonTooltip) {
            this.tooltip = createTooltip(this.leafletPoly, settings);
            this.updateTooltip(polyData);
        }
        if (onClickListener) {
            this.leafletPoly.on('click', onClickListener);
        }
    }

    updateTooltip(data: DatasourceData) {
        const pattern = this.settings.useTooltipFunction ?
            safeExecute(this.settings.tooltipFunction, [this.data, this.dataSources, this.data.dsIndex]) : this.settings.tooltipPattern;
        this.tooltip.setContent(parseWithTranslation.parseTemplate(pattern, data, true));
    }

    updatePolygon(data: LatLngTuple[], dataSources: DatasourceData[], settings: PolygonSettings) {
        this.data = data;
        this.dataSources = dataSources;
        this.leafletPoly.setLatLngs(data);
        if (settings.showPolygonTooltip)
            this.updateTooltip(this.data);
        this.updatePolygonColor(settings);
    }

    removePolygon() {
        this.map.removeLayer(this.leafletPoly);
    }

    updatePolygonColor(settings) {
        const style: L.PathOptions = {
            fill: true,
            fillColor: settings.polygonColor,
            color: settings.polygonStrokeColor,
            weight: settings.polygonStrokeWeight,
            fillOpacity: settings.polygonOpacity,
            opacity: settings.polygonStrokeOpacity
        };
        this.leafletPoly.setStyle(style);
    }

    getPolygonLatLngs() {
        return this.leafletPoly.getLatLngs();
    }

    setPolygonLatLngs(latLngs: LatLngExpression[]) {
        this.leafletPoly.setLatLngs(latLngs);
        this.leafletPoly.redraw();
    }
}
