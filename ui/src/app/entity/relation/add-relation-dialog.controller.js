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
/*@ngInject*/
export default function AddRelationDialogController($scope, $mdDialog, types, entityRelationService, direction, entityId) {

    var vm = this;

    vm.types = types;
    vm.direction = direction;
    vm.targetEntityId = {};

    vm.relation = {};
    if (vm.direction == vm.types.entitySearchDirection.from) {
        vm.relation.from = entityId;
    } else {
        vm.relation.to = entityId;
    }
    vm.relation.type = types.entityRelationType.contains;

    vm.add = add;
    vm.cancel = cancel;

    function cancel() {
        $mdDialog.cancel();
    }

    function add() {
        if (vm.direction == vm.types.entitySearchDirection.from) {
            vm.relation.to = vm.targetEntityId;
        } else {
            vm.relation.from = vm.targetEntityId;
        }
        $scope.theForm.$setPristine();
        entityRelationService.saveRelation(vm.relation).then(
            function success() {
                $mdDialog.hide();
            }
        );
    }

}
