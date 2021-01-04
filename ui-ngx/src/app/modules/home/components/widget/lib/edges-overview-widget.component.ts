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

import { ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { WidgetContext } from '@home/models/widget-component.models';
import { WidgetConfig } from '@shared/models/widget.models';
import { IWidgetSubscription } from '@core/api/widget-api.models';
import { UtilsService } from '@core/services/utils.service';
import { LoadNodesCallback } from '@shared/components/nav-tree.component';
import { EntityType } from '@shared/models/entity-type.models';
import {
  EdgeGroupNodeData,
  edgeGroupsNodeText,
  edgeGroupsTypes,
  edgeNodeText,
  EdgeOverviewNode,
  EntityNodeData,
  EntityNodeDatasource
} from '@home/components/widget/lib/edges-overview-widget.models';
import { EdgeService } from "@core/http/edge.service";
import { EntityService } from "@core/http/entity.service";
import { TranslateService } from "@ngx-translate/core";
import { PageLink } from "@shared/models/page/page-link";
import { Edge } from "@shared/models/edge.models";
import { BaseData } from "@shared/models/base-data";
import { EntityId } from "@shared/models/id/entity-id";
import { getCurrentAuthUser } from "@core/auth/auth.selectors";
import { Authority } from "@shared/models/authority.enum";

@Component({
  selector: 'tb-edges-overview-widget',
  templateUrl: './edges-overview-widget.component.html',
  styleUrls: ['./edges-overview-widget.component.scss']
})
export class EdgesOverviewWidgetComponent extends PageComponent implements OnInit {

  @Input()
  ctx: WidgetContext;

  public toastTargetId = 'edges-overview-' + this.utils.guid();
  public customerTitle: string = null;

  private widgetConfig: WidgetConfig;
  private subscription: IWidgetSubscription;
  private datasources: Array<EntityNodeDatasource>;

  private nodeIdCounter = 0;

  private edgeNodesMap: {[parentNodeId: string]: {[edgeId: string]: string}} = {};
  private edgeGroupsNodesMap: {[edgeNodeId: string]: {[groupType: string]: string}} = {};

  constructor(protected store: Store<AppState>,
              private edgeService: EdgeService,
              private entityService: EntityService,
              private translateService: TranslateService,
              private utils: UtilsService,
              private cd: ChangeDetectorRef) {
    super(store);
  }

  ngOnInit(): void {
    this.widgetConfig = this.ctx.widgetConfig;
    this.subscription = this.ctx.defaultSubscription;
    this.datasources = this.subscription.datasources as Array<EntityNodeDatasource>;
    if (this.datasources.length > 0 && this.datasources[0].entity.id.entityType === EntityType.EDGE) {
      let selectedEdge = this.datasources[0].entity;
      this.getCustomerTitle(selectedEdge.id.id);
      this.ctx.widgetTitle = selectedEdge.name;
    }
    this.ctx.updateWidgetParams();
  }

  public loadNodes: LoadNodesCallback = (node, cb) => {
    var selectedEdge: BaseData<EntityId> = null;
    if (this.datasources.length > 0 && this.datasources[0].entity && this.datasources[0].entity.id.entityType === EntityType.EDGE) {
      selectedEdge = this.datasources[0].entity;
    }
    if (node.id === '#' && selectedEdge) {
      cb(this.loadNodesForEdge(selectedEdge.id.id, selectedEdge));
    } else if (node.data && node.data.type === 'edgeGroup') {
      const pageLink = new PageLink(100);
      this.entityService.getAssignedToEdgeEntitiesByType(node, pageLink).subscribe(
        (entities) => {
          if (entities.data.length > 0) {
            cb(this.edgesToNodes(node.id, entities.data));
          } else {
            cb([]);
          }
        }
      )
    } else {
      cb([]);
    }
  }

  private loadNodesForEdge(parentNodeId: string, edge: any): EdgeOverviewNode[] {
    const nodes: EdgeOverviewNode[] = [];
    const nodesMap = {};
    this.edgeGroupsNodesMap[parentNodeId] = nodesMap;
    const authUser = getCurrentAuthUser(this.store);
    var allowedGroupTypes: EntityType[] = edgeGroupsTypes;
    if (authUser.authority === Authority.CUSTOMER_USER) {
      allowedGroupTypes = edgeGroupsTypes.filter(type => type !== EntityType.RULE_CHAIN);
    }
    allowedGroupTypes.forEach((entityType) => {
      const node: EdgeOverviewNode = {
        id: (++this.nodeIdCounter)+'',
        icon: false,
        text: edgeGroupsNodeText(this.translateService, entityType),
        children: true,
        data: {
          type: 'edgeGroup',
          entityType,
          edge,
          internalId: edge.id.id + '_' + entityType
        } as EdgeGroupNodeData
      };
      nodes.push(node);
      nodesMap[entityType] = node.id;
    });
    return nodes;
  }

  private createEdgeNode(parentNodeId: string, edge: Edge): EdgeOverviewNode {
    let nodesMap = this.edgeNodesMap[parentNodeId];
    if (!nodesMap) {
      nodesMap = {};
      this.edgeNodesMap[parentNodeId] = nodesMap;
    }
    const node: EdgeOverviewNode = {
      id: (++this.nodeIdCounter)+'',
      icon: false,
      text: edgeNodeText(edge),
      children: parentNodeId === '#',
      state: {
        disabled: false
      },
      data: {
        type: 'entity',
        entity: edge,
        internalId: edge.id.id
      } as EntityNodeData
    };
    nodesMap[edge.id.id] = node.id;
    return node;
  }

  private edgesToNodes(parentNodeId: string, edges: Array<Edge>): EdgeOverviewNode[] {
    const nodes: EdgeOverviewNode[] = [];
    this.edgeNodesMap[parentNodeId] = {};
    if (edges) {
      edges.forEach((edge) => {
        const node = this.createEdgeNode(parentNodeId, edge);
        nodes.push(node);
      });
    }
    return nodes;
  }

  private getCustomerTitle(edgeId) {
    this.edgeService.getEdgeInfo(edgeId).subscribe(
      (edge) => {
        if (edge.customerTitle) {
          this.customerTitle = this.translateService.instant('edge.assigned-to-customer', {customerTitle: edge.customerTitle});
        } else {
          this.customerTitle = null;
        }
        this.cd.detectChanges();
      });
  }
}
