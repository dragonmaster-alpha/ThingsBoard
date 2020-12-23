/*
 * Copyright © 2016-2020 The Thingsboard Authors
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
/* eslint-disable import/no-unresolved, import/default */

import edgeFieldsetTemplate from './edge-fieldset.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export default function EdgeDirective($compile, $templateCache, $translate, $mdDialog, $document, utils, toast, types, customerService, edgeService) {
    var linker = function (scope, element) {
        var template = $templateCache.get(edgeFieldsetTemplate);
        element.html(template);

        scope.types = types;
        scope.isAssignedToCustomer = false;
        scope.isPublic = false;
        scope.assignedCustomer = null;

        scope.$watch('edge', function(newVal) {
            if (newVal) {
                if (!scope.edge.id) {
                    scope.edge.routingKey = utils.guid('');
                    scope.edge.secret = generateSecret(20);
                    scope.edge.cloudEndpoint = utils.baseUrl();
                    scope.edge.type = 'default';
                }
                if (scope.edge.customerId && scope.edge.customerId.id !== types.id.nullUid) {
                    scope.isAssignedToCustomer = true;
                    customerService.getShortCustomerInfo(scope.edge.customerId.id).then(
                        function success(customer) {
                            scope.assignedCustomer = customer;
                            scope.isPublic = customer.isPublic;
                        }
                    );
                } else {
                    scope.isAssignedToCustomer = false;
                    scope.isPublic = false;
                    scope.assignedCustomer = null;
                }
            }
        });

        function generateSecret(length) {
            if (angular.isUndefined(length) || length == null) {
                length = 1;
            }
            var l = length > 10 ? 10 : length;
            var str = Math.random().toString(36).substr(2, l);
            if(str.length >= length){
                return str;
            }
            return str.concat(generateSecret(length - str.length));
        }

        scope.onEdgeIdCopied = function() {
            toast.showSuccess($translate.instant('edge.id-copied-message'), 750, angular.element(element).parent().parent(), 'bottom left');
        };

        scope.onEdgeSync = function (edgeId) {
            edgeService.syncEdge(edgeId).then(
                function success() {
                    toast.showSuccess($translate.instant('edge.sync-message'), 750, angular.element(element).parent().parent(), 'bottom left');
                },
                function fail(error) {
                    toast.showError(error);
                }
            );
        }

        $compile(element.contents())(scope);

        scope.onEdgeInfoCopied = function(type) {
            let infoTypeLabel = "";
            switch (type) {
                case 'key':
                    infoTypeLabel = "edge.edge-key-copied-message";
                    break;
                case 'secret':
                    infoTypeLabel = "edge.edge-secret-copied-message";
                    break;
            }
            toast.showSuccess($translate.instant(infoTypeLabel), 750, angular.element(element).parent().parent(), 'bottom left');
        };

    };
    return {
        restrict: "E",
        link: linker,
        scope: {
            edge: '=',
            isEdit: '=',
            edgeScope: '=',
            theForm: '=',
            onAssignToCustomer: '&',
            onMakePublic: '&',
            onUnassignFromCustomer: '&',
            onManageEdgeAssets: '&',
            onManageEdgeDevices: '&',
            onManageEdgeEntityViews: '&',
            onManageEdgeDashboards: '&',
            onManageEdgeRuleChains: '&',
            onDeleteEdge: '&'
        }
    };
}
