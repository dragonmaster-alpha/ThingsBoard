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
/* eslint-disable import/no-unresolved, import/default */

import selectWidgetTypeTemplate from './select-widget-type.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export default function WidgetLibraryController($scope, $rootScope, widgetService, userService,
                                                $state, $stateParams, $document, $mdDialog, $translate, $filter, types) {

    var vm = this;

    var widgetsBundleId = $stateParams.widgetsBundleId;

    vm.widgetsBundle;
    vm.widgetTypes = [];

    vm.noData = noData;
    vm.addWidgetType = addWidgetType;
    vm.openWidgetType = openWidgetType;
    vm.removeWidgetType = removeWidgetType;
    vm.loadWidgetLibrary = loadWidgetLibrary;
    vm.addWidgetType = addWidgetType;
    vm.isReadOnly = isReadOnly;

    function loadWidgetLibrary() {
        $rootScope.loading = true;
        widgetService.getWidgetsBundle(widgetsBundleId).then(
            function success(widgetsBundle) {
                vm.widgetsBundle = widgetsBundle;
                if (vm.widgetsBundle) {
                    var bundleAlias = vm.widgetsBundle.alias;
                    var isSystem = vm.widgetsBundle.tenantId.id === types.id.nullUid;

                    widgetService.getBundleWidgetTypes(bundleAlias, isSystem).then(
                        function (widgetTypes) {

                            widgetTypes = $filter('orderBy')(widgetTypes, ['-descriptor.type','name']);

                            var top = 0;
                            var lastTop = [0, 0, 0];
                            var col = 0;
                            var column = 0;

                            if (widgetTypes.length > 0) {
                                loadNext(0);
                            } else {
                                $rootScope.loading = false;
                            }

                            function loadNextOrComplete(i) {
                                i++;
                                if (i < widgetTypes.length) {
                                    loadNext(i);
                                } else {
                                    $rootScope.loading = false;
                                }
                            }

                            function loadNext(i) {
                                var widgetType = widgetTypes[i];
                                $scope.$applyAsync(function() {
                                    var widgetTypeInfo = widgetService.toWidgetInfo(widgetType);
                                    var sizeX = 8;
                                    var sizeY = Math.floor(widgetTypeInfo.sizeY);
                                    var widget = {
                                        id: widgetType.id,
                                        isSystemType: isSystem,
                                        bundleAlias: bundleAlias,
                                        typeAlias: widgetTypeInfo.alias,
                                        type: widgetTypeInfo.type,
                                        title: widgetTypeInfo.widgetName,
                                        sizeX: sizeX,
                                        sizeY: sizeY,
                                        row: top,
                                        col: col,
                                        config: angular.fromJson(widgetTypeInfo.defaultConfig)
                                    };
                                    widget.config.title = widgetTypeInfo.widgetName;
                                    vm.widgetTypes.push(widget);
                                    top+=sizeY;
                                    if (top > lastTop[column] + 10) {
                                        lastTop[column] = top;
                                        column++;
                                        if (column > 2) {
                                            column = 0;
                                        }
                                        top = lastTop[column];
                                        col = column * 8;
                                    }
                                    loadNextOrComplete(i);
                                });
                           }
                        }
                    );
                } else {
                    $rootScope.loading = false;
                }
            }, function fail() {
                $rootScope.loading = false;
            }
        );
    }

    function noData() {
        return vm.widgetTypes.length == 0;
    }

    function addWidgetType($event) {
        vm.openWidgetType($event);
    }

    function isReadOnly() {
        if (userService.getAuthority() === 'TENANT_ADMIN') {
            return !vm.widgetsBundle || vm.widgetsBundle.tenantId.id === types.id.nullUid;
        } else {
            return userService.getAuthority() != 'SYS_ADMIN';
        }
    }

    function openWidgetType(event, widget) {
        if (event) {
            event.stopPropagation();
        }
        if (widget) {
            $state.go('home.widgets-bundles.widget-types.widget-type',
                {widgetTypeId: widget.id.id});
        } else {
            $mdDialog.show({
                controller: 'SelectWidgetTypeController',
                controllerAs: 'vm',
                templateUrl: selectWidgetTypeTemplate,
                parent: angular.element($document[0].body),
                fullscreen: true,
                targetEvent: event
            }).then(function (widgetType) {
                $state.go('home.widgets-bundles.widget-types.widget-type',
                    {widgetType: widgetType});
            }, function () {
            });
        }
    }

    function removeWidgetType(event, widget) {
        var confirm = $mdDialog.confirm()
            .targetEvent(event)
            .title($translate.instant('widget.remove-widget-type-title', {widgetName: widget.config.title}))
            .htmlContent($translate.instant('widget.remove-widget-type-text'))
            .ariaLabel($translate.instant('widget.remove-widget-type'))
            .cancel($translate.instant('action.no'))
            .ok($translate.instant('action.yes'));
        $mdDialog.show(confirm).then(function () {
            widgetService.deleteWidgetType(widget.bundleAlias, widget.typeAlias, widget.isSystemType).then(
                function success() {
                    vm.widgetTypes.splice(vm.widgetTypes.indexOf(widget), 1);
                },
                function fail() {}
            );
        });
    }
}
