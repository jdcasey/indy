'use strict';

/* Controllers */

var aproxControllers = angular.module('aprox.controllers', []);

aproxControllers.controller('NavCtl', ['$scope', function($scope){
  $scope.addon_navs = [];
  if ( addons !== undefined ){
    addons.items.each(function(addon){
      if ( addon.sections !== undefined ){
        addon.sections.each(function(section){
          $scope.addon_navs.push(section);
        });
      }
    });
  }
}]);

aproxControllers.controller('RemoteListCtl', ['$scope', 'RemoteSvc', 'StoreUtilSvc', function($scope, RemoteSvc, StoreUtilSvc) {
    $scope.listing = RemoteSvc.query({}, function(listing){
      for(var i=0; i<listing.items.length; i++){
        var item = listing.items[i];
        item.detailHref = StoreUtilSvc.detailHref(item.key);
        item.storeHref = StoreUtilSvc.storeHref(item.key);
        item.name = StoreUtilSvc.nameFromKey(item.key);
        item.description = StoreUtilSvc.defaultDescription(item.description);
      }
    });
    $scope.storeUtils = StoreUtilSvc;
    $scope.orderProp = 'key';
  }]);

aproxControllers.controller('RemoteDetailCtl', ['$scope', '$routeParams', '$location', 'RemoteSvc', 'StoreUtilSvc', function($scope, $routeParams, $location, RemoteSvc, StoreUtilSvc) {
  $scope.raw = {
    name: '',
  };

  $scope.store = RemoteSvc.get({name: $routeParams.name}, function(store){
      $scope.raw.name = StoreUtilSvc.nameFromKey(store.key);
      $scope.raw.editHref = StoreUtilSvc.editHref(store.key);
      $scope.raw.storeHref = StoreUtilSvc.storeHref(store.key);
      $scope.raw.description = StoreUtilSvc.defaultDescription(store.description);

      var useX509 = store.server_certificate_pem !== undefined;
      useX509 = store.key_certificate_pem !== undefined || useX509;

      $scope.raw.use_x509 = useX509;

      var useProxy = store.proxy_host !== undefined;
      $scope.raw.use_proxy = useProxy;

      var useAuth = (useProxy && store.proxy_user !== undefined);
      useAuth = store.user !== undefined || useAuth;

      $scope.raw.use_auth = useAuth;
  });

  $scope.storeUtils = StoreUtilSvc;

  $scope.delete = function(){
    if ( confirm( "Really delete '" + $scope.raw.name + "'??") )
    {
      RemoteSvc.delete({name: $scope.raw.name}, function(){
        $location.path( '/remote' );
      });
    }
    else{
      $location.path( '/remote' );
    }
  };

}]);

aproxControllers.controller('RemoteEditCtl', ['$scope', '$routeParams', '$location', 'RemoteSvc', 'StoreUtilSvc', function($scope, $routeParams, $location, RemoteSvc, StoreUtilSvc) {
  $scope.editMode = window.location.hash.startsWith( "#/remote/edit" );
  $scope.storeUtils = StoreUtilSvc;

  $scope.raw = {
    name: '',
    timeout_seconds: '',
    cache_timeout_seconds: '',
  };

  if ( $scope.editMode ){
    $scope.store = RemoteSvc.get({name: $routeParams.name}, function(store){
      $scope.raw.name = StoreUtilSvc.nameFromKey(store.key);
      $scope.raw.storeHref = StoreUtilSvc.storeHref(store.key);
      $scope.raw.cache_timeout_seconds = StoreUtilSvc.secondsToDuration(store.cache_timeout_seconds);
      $scope.raw.timeout_seconds = StoreUtilSvc.secondsToDuration(store.timeout_seconds);

      var useX509 = store.server_certificate_pem !== undefined;
      useX509 = store.key_certificate_pem !== undefined || useX509;

      $scope.raw.use_x509 = useX509;

      var useProxy = store.proxy_host !== undefined;
      $scope.raw.use_proxy = useProxy;

      var useAuth = useProxy && store.proxy_user !== undefined;
      useAuth = store.user !== undefined || useAuth;

      $scope.raw.use_auth = useAuth;
    });
  }
  else{
    $scope.store = {
      url: '',
      timeout_seconds: 60,
      cache_timeout_seconds: 86400,
      is_passthrough: false
    };
  }

  $scope.save = function(){
    if ( $scope.is_passthrough ){
      delete $scope.store.cache_timeout_seconds;
    }
    else{
      $scope.store.cache_timeout_seconds = StoreUtilSvc.durationToSeconds($scope.raw.cache_timeout_seconds);
    }

    $scope.store.timeout_seconds = StoreUtilSvc.durationToSeconds($scope.raw.timeout_seconds);

    if ( $scope.editMode ){
      RemoteSvc.update({name: $scope.raw.name}, $scope.store, function(){
        $location.path( StoreUtilSvc.detailPath($scope.store.key) );
      });
    }
    else{
      $scope.store.key = StoreUtilSvc.formatKey('remote', $scope.raw.name);
      RemoteSvc.create({}, $scope.store, function(){
        $location.path( StoreUtilSvc.detailPath($scope.store.key) );
      });
    }
  };

  $scope.delete = function(){
    if ( confirm( "Really delete '" + $scope.raw.name + "'??") )
    {
      RemoteSvc.delete({name: $scope.raw.name}, function(){
        $location.path( '/remote' );
      });
    }
    else{
      $location.path( '/remote' );
    }
  };

  $scope.cancel = function(){
    if ( $scope.editMode ){
      $location.path( StoreUtilSvc.detailPath($scope.store.key) );
    }
    else{
      $location.path( '/remote' );
    }
  };

}]);

aproxControllers.controller('HostedListCtl', ['$scope', 'HostedSvc', 'StoreUtilSvc', function($scope, HostedSvc, StoreUtilSvc) {
    $scope.listing = HostedSvc.query({}, function(listing){
      for(var i=0; i<listing.items.length; i++){
        var item = listing.items[i];
        item.detailHref = StoreUtilSvc.detailHref(item.key);
        item.storeHref = StoreUtilSvc.storeHref(item.key);
        item.name = StoreUtilSvc.nameFromKey(item.key);
        item.hostedOptions = StoreUtilSvc.hostedOptions(item);
        item.description = StoreUtilSvc.defaultDescription(item.description);
      }
    });

    $scope.storeUtils = StoreUtilSvc;
    $scope.orderProp = 'key';
  }]);

aproxControllers.controller('HostedDetailCtl', ['$scope', '$routeParams', '$location', 'HostedSvc', 'StoreUtilSvc', function($scope, $routeParams, $location, HostedSvc, StoreUtilSvc) {
  $scope.raw = {};

  $scope.store = HostedSvc.get({name: $routeParams.name}, function(store){
    $scope.raw.name = StoreUtilSvc.nameFromKey(store.key);
    $scope.raw.storeHref = StoreUtilSvc.storeHref(store.key);
    $scope.raw.editHref = StoreUtilSvc.editHref(store.key);
    $scope.raw.description = StoreUtilSvc.defaultDescription(store.description);
  });
  $scope.storeUtils = StoreUtilSvc;

  $scope.allowUploads = function(store){
    return store.allow_snapshots || store.allow_releases;
  };

  $scope.showSnapshotTimeout = function(store){
    return store.allow_snapshots;
  };

  $scope.delete = function(){
    if ( confirm( "Really delete '" + $scope.raw.name + "'??") )
    {
      HostedSvc.delete({name: $scope.raw.name}, function(){
        $location.path( '/hosted' );
      });
    }
    else{
      $location.path( '/hosted' );
    }
  };

}]);

aproxControllers.controller('HostedEditCtl', ['$scope', '$routeParams', '$location', 'HostedSvc', 'StoreUtilSvc', function($scope, $routeParams, $location, HostedSvc, StoreUtilSvc) {
  $scope.editMode = window.location.hash.startsWith( "#/hosted/edit" );
  $scope.storeUtils = StoreUtilSvc;

  $scope.raw = {
    name: '',
    snapshot_timeout_seconds: '',
  };

  if ( $scope.editMode ){
    $scope.store = HostedSvc.get({name: $routeParams.name}, function(store){
      $scope.raw.name = $scope.storeUtils.nameFromKey(store.key);
      $scope.raw.snapshotTimeoutSeconds = StoreUtilSvc.secondsToDuration(store.snapshotTimeoutSeconds);
    });
  }
  else{
    $scope.store = {
      allow_releases: true,
      allow_snapshots: true,
      snapshotTimeoutSeconds: 86400,
    };
  }

  $scope.allowUploads = function(store){
    return store.allow_snapshots || store.allow_releases;
  };

  $scope.save = function(){
    if (!$scope.store.allow_snapshots){
      delete $scope.store.snapshotTimeoutSeconds;
    }
    else{
      $scope.store.snapshotTimeoutSeconds = StoreUtilSvc.durationToSeconds($scope.raw.snapshotTimeoutSeconds);
    }

    if ( $scope.editMode ){
      HostedSvc.update({name: $scope.raw.name}, $scope.store, function(){
        $location.path( StoreUtilSvc.detailPath($scope.store.key) );
      });
    }
    else{
      $scope.store.key = StoreUtilSvc.formatKey('hosted', $scope.raw.name);
      HostedSvc.create({}, $scope.store, function(){
        $location.path( StoreUtilSvc.detailPath($scope.store.key) );
      });
    }
  };

  $scope.delete = function(){
    if ( confirm( "Really delete '" + $scope.raw.name + "'??") )
    {
      HostedSvc.delete({name: $scope.raw.name}, function(){
        $location.path( '/hosted' );
      });
    }
    else{
      $location.path( '/hosted' );
    }
  };

  $scope.cancel = function(){
    if ( $scope.editMode ){
      $location.path( StoreUtilSvc.detailPath($scope.store.key) );
    }
    else{
      $location.path( '/hosted' );
    }
  };

}]);

aproxControllers.controller('GroupListCtl', ['$scope', 'GroupSvc', 'StoreUtilSvc', function($scope, GroupSvc, StoreUtilSvc) {
    $scope.listing = GroupSvc.query({}, function(listing){
      for(var i=0; i<listing.items.length; i++){
        var item = listing.items[i];
        item.detailHref = StoreUtilSvc.detailHref(item.key);
        item.storeHref = StoreUtilSvc.storeHref(item.key);
        item.name = StoreUtilSvc.nameFromKey(item.key);
        item.description = StoreUtilSvc.defaultDescription(item.description);
      }
    });

    $scope.storeUtils = StoreUtilSvc;
    $scope.orderProp = 'key';
  }]);

aproxControllers.controller('GroupDetailCtl', ['$scope', '$routeParams', '$location', 'GroupSvc', 'StoreUtilSvc', function($scope, $routeParams, $location, GroupSvc, StoreUtilSvc) {
  $scope.raw = {
    constituentHrefs: {},
  };

  $scope.store = GroupSvc.get({name: $routeParams.name}, function(store){
    $scope.raw.name = StoreUtilSvc.nameFromKey(store.key);
    $scope.raw.editHref = StoreUtilSvc.editHref(store.key);
    $scope.raw.storeHref = StoreUtilSvc.storeHref(store.key);
    $scope.raw.description = StoreUtilSvc.defaultDescription(store.description);

    for(var i=0; i<store.constituents.length; i++){
      var item = store.constituents[i];
      $scope.raw.constituentHrefs[item] = {
        detailHref: StoreUtilSvc.detailHref(item),
      };
    }
  });
  $scope.storeUtils = StoreUtilSvc;

  $scope.delete = function(){
    if ( confirm( "Really delete '" + $scope.raw.name + "'??") )
    {
      GroupSvc.delete({name: $scope.raw.name}, function(){
        $location.path( '/group' );
      });
    }
    else{
      $location.path( '/group' );
    }
  };

}]);

aproxControllers.controller('GroupEditCtl', ['$scope', '$routeParams', '$location', 'GroupSvc', 'StoreUtilSvc', 'AllEndpointsSvc', function($scope, $routeParams, $location, GroupSvc, StoreUtilSvc, AllEndpointsSvc) {
  $scope.editMode = window.location.hash.startsWith( "#/group/edit" );
  $scope.storeUtils = StoreUtilSvc;

  $scope.raw = {
    name: '',
    available: [],
  };

  if ( $scope.editMode ){
    $scope.store = GroupSvc.get({name: $routeParams.name}, function(store){
      $scope.raw.name = $scope.storeUtils.nameFromKey(store.key);
    });
  }
  else{
    $scope.store = {
      constituents: [],
    };
  }

  AllEndpointsSvc.query(function(listing){
    $scope.raw.available = StoreUtilSvc.sortEndpoints( listing.items );
  });

  $scope.addConstituent = function(constituent){
    alert("FOO" + constituent);
  };

  $scope.save = function(){
    if ( $scope.editMode ){
      GroupSvc.update({name: $scope.raw.name}, $scope.store, function(){
        $location.path( StoreUtilSvc.detailPath($scope.store.key) );
      });
    }
    else{
      $scope.store.key = StoreUtilSvc.formatKey('group', $scope.raw.name);
      GroupSvc.create({}, $scope.store, function(){
        $location.path( StoreUtilSvc.detailPath($scope.store.key) );
      });
    }
  };

  $scope.delete = function(){
    if ( confirm( "Really delete '" + $scope.raw.name + "'??") )
    {
      GroupSvc.delete({name: $scope.raw.name}, function(){
        $location.path( '/group' );
      });
    }
    else{
      $location.path( '/group' );
    }
  };

  $scope.cancel = function(){
    if ( $scope.editMode ){
      $location.path( StoreUtilSvc.detailPath($scope.store.key) );
    }
    else{
      $location.path( '/group' );
    }
  };

}]);

aproxControllers.controller('NfcController', ['$scope', '$routeParams', '$location', 'NfcSvc', 'StoreUtilSvc', 'AllEndpointsSvc', 
                                              function($scope, $routeParams, $location, NfcSvc, StoreUtilSvc, AllEndpointsSvc){
  $scope.raw = {
    available: [],
  };
  
  $scope.clearAllNFC = function(){
    NfcSvc.deleteAll();
    $location.path('/nfc');
  };
  
  $scope.clearSection = function(section){
    var key = section.key;
    
//    alert( "Clear all NFC entries for: " + key );
    
    var name=StoreUtilSvc.nameFromKey(key);
    var type = StoreUtilSvc.typeFromKey(key);
    
    NfcSvc.delete({name: name, type: type},
      function(){
        $scope.message = {type: 'OK', message: 'Cleared NFC for ' + key + "'"};
//        alert( "NFC for " + key + " has been cleared!");
      }, 
      function(error){
        $scope.message = {type: 'ERROR', message: 'Failed to clear NFC for ' + key + "'", detail: error};
//        alert('[ERROR] Failed to clear NFC for ' + key + "'\n" + error );
      }
    );
    
    section.paths = [];
  };
  
  $scope.clearSectionPath = function(section, path){
    path = path.substring(1);
    var key = section.key;
    
//    alert( "Clear all NFC entries for: " + key + ", path: " + path );
    
    var name=StoreUtilSvc.nameFromKey(key);
    var type = StoreUtilSvc.typeFromKey(key);
    
    NfcSvc.delete({name: name, type: type, path: path},
      function(){
        $scope.message = {type: 'OK', message: 'Cleared NFC for ' + key + "', path: " + path};
//        alert( "NFC for: " + key + ", path: " + path + " has been cleared!" );
      }, 
      function(error){
        $scope.message = {type: 'ERROR', message: 'Failed to clear NFC for ' + key + "'", detail: error};
//        alert('[ERROR] Failed to clear NFC for ' + key + "', path: " + path + "\n" + error );
      }
    );
    
    var idx = section.paths.indexOf(path);
    section.paths.splice(idx,1);
  };
  
  $scope.showAll = function(){
    if ( !window.location.hash.startsWith( "#/nfc/view/all" ) ){
      $location.path('/nfc/view/all');
    }
  };
  
  $scope.show = function(){
    if ( !$scope.currentKey ){return;}
    
    var viewPath = '/nfc/view/' + $scope.currentKey.replace(':', '/');
    
    if ( !window.location.hash.startsWith( "#" + viewPath ) ){
      $location.path(viewPath);
    }
  };
  
  AllEndpointsSvc.query(function(listing){
    var available = [];
    listing.items.each(function(item){
      item.key = StoreUtilSvc.formatKey(item.type, item.name);
      item.label = StoreUtilSvc.keyLabel(item.key);
      available.push(item);
    });
    
    $scope.raw.available = StoreUtilSvc.sortEndpoints( available );
  });
  
  if ( window.location.hash == ( "#/nfc/view/all" ) ){
//    alert( "showing all NFC entries");
    delete $scope.currentKey;
    
    NfcSvc.query({}, function(nfc){
      nfc.sections.each(function(section){
        section.label = StoreUtilSvc.keyLabel(section.key);
        section.paths.sort();
      });
      
      $scope.sections = StoreUtilSvc.sortByEmbeddedKey(nfc.sections);
    });
  }
  else{
    var routeType = $routeParams.type;
    var routeName = $routeParams.name;
    
    if ( routeType !== undefined && routeName !== undefined ){
      $scope.currentKey = StoreUtilSvc.formatKey(routeType, routeName);
      
//      alert( "showing NFC entries for: " + $scope.currentKey);
      
      NfcSvc.get({type:routeType, name:routeName}, function(nfc){
        nfc.sections.each(function(section){
          section.label = StoreUtilSvc.keyLabel(section.key);
          section.paths.sort();
        });
        
        $scope.sections = StoreUtilSvc.sortByEmbeddedKey(nfc.sections);
      });
    }
  }
}]);

aproxControllers.controller('FooterCtl', ['$scope', 'FooterSvc', function($scope, FooterSvc){
    $scope.stats = FooterSvc.query();
  }]);

