/* Copyright (c) 2019 BlackBerry Limited.  All Rights Reserved.
* 
* Licensed under the Apache License, Version 2.0 (the "License"); 
* you may not use this file except in compliance with the License. 
* You may obtain a copy of the License at 
* 
* http://www.apache.org/licenses/LICENSE-2.0 
* 
* Unless required by applicable law or agreed to in writing, software 
* distributed under the License is distributed on an "AS IS" BASIS, 
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
* See the License for the specific language governing permissions and 
* limitations under the License. 
  
* This sample code was created by BlackBerry using SDKs from Apple Inc. 
* and may contain code licensed for use only with Apple products. 
* Please review your Apple SDK Agreement for additional details. 
*/ 


#import "LocationSharingViewController.h"
#import "LocationSharingApp.h"
#import <BBMEnterprise/BBMEnterprise.h>
#import <MapKit/MKMapView.h>
#import <MapKit/MKPolyline.h>
#import <MapKit/MKPolylineRenderer.h>
#import <MapKit/MKUserLocation.h>
#import "BBMAccess.h"
#import "LocationMessageLoader.h"
#import "BBMChatMessage+LocationApp.h"
#import "BBMChat+LocationApp.h"
#import "LocationMapAnnotation.h"

@interface LocationSharingViewController () <MKMapViewDelegate>

@property (nonatomic, weak) IBOutlet MKMapView *mapView;
@property (nonatomic, assign) BOOL hasShownInitialLocation;

@property (nonatomic, strong) BBMChat *chat;
@property (nonatomic, strong) ObservableMonitor *titleMonitor;
@property (nonatomic, strong) LocationMessageLoader *messageLoader;
@property (nonatomic, strong) NSDictionary *locationMessagesByRegId;
@property (nonatomic, strong) UIColor *currentColor;
@property (nonatomic, assign) BOOL showAllLocations;
@property (nonatomic, weak) IBOutlet UIBarButtonItem *showAllLocationsBarButton;

@end

@implementation LocationSharingViewController

- (void)dealloc
{
#if DEBUG
    // Xcode8/iOS10 MKMapView bug workaround
    // This is a workaround for a bug where the app freezes when the map view is deallocated. This
    // only happens when the app is being run through xcode so only apply the workaround for DEBUG
    // builds.
    // See http://stackoverflow.com/a/39769891
    static NSMutableArray *unusedObjects;
    if (!unusedObjects) {
        unusedObjects = [NSMutableArray new];
    }
    [unusedObjects addObject:_mapView];
#endif
}

- (void)viewDidLoad {
    [super viewDidLoad];
    
    self.chat = [BBMAccess getChatForId:self.chatId];

    //Create a label to use as a custom title view to allow us to add a tap recognizer to it.
    //Tapping the title will allow the user to change the subject of the chat.
    UILabel *titleView = [[UILabel alloc] init];
    titleView.font = [UIFont boldSystemFontOfSize:17];
    titleView.userInteractionEnabled = YES;
    titleView.textAlignment = NSTextAlignmentCenter;
    [titleView addGestureRecognizer:[[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(updateChatSubject)]];
    self.navigationItem.titleView = titleView;
    
    [self updateTitle];
}

- (void)viewWillAppear:(BOOL)animated
{
    self.mapView.delegate = self;
    self.mapView.showsUserLocation = YES;
}

- (void)viewWillDisappear:(BOOL)animated
{
    self.mapView.delegate = nil;
    self.mapView.showsUserLocation = NO;
}

- (void)observeMessages
{
    typeof(self) __weak weakSelf = self;
    //This loads the location messages for this chat. Every time a new message arrives this
    //block will run.
    self.messageLoader = [LocationMessageLoader messageLoaderForConversation:self.chat callback:^(NSDictionary *locationMessagesByRegId) {
        //Update the map with the locations received from the message loader
        weakSelf.locationMessagesByRegId = locationMessagesByRegId;
        [weakSelf refreshMap];
    }];
}

- (void)updateTitle
{
    typeof(self) __weak weakSelf = self;
    //The method chatTitle accesses a few getters that are observables so any change
    //in those values will trigger this monitor
    self.titleMonitor = [ObservableMonitor monitorActivatedWithName:@"chatTableTitleMonitor" block:^{
        //Set title
        UILabel *titleLabel = (UILabel *)weakSelf.navigationItem.titleView;
        titleLabel.text = [weakSelf.chat chatTitle];
        [titleLabel sizeToFit];
    }];
}

- (void)updateChatSubject
{
    UIAlertController *controller = [UIAlertController alertControllerWithTitle:@"Update Subject"
                                                                        message:@"Enter a subject for the chat."
                                                                 preferredStyle:UIAlertControllerStyleAlert];
    UIAlertAction *okAction = [UIAlertAction actionWithTitle:@"Update"
                                                       style:UIAlertActionStyleDefault
                                                     handler:^(UIAlertAction *action) {
                                                         NSString *subject = controller.textFields[0].text;
                                                         if (![subject isEqualToString:self.chat.subject]) {
                                                             [BBMAccess updateChatSubject:subject forChat:self.chat];
                                                         }
                                                     }];
    [controller addAction:okAction];
    
    UIAlertAction *cancelAction = [UIAlertAction actionWithTitle:@"Cancel"
                                                           style:UIAlertActionStyleCancel
                                                         handler:nil];
    [controller addAction:cancelAction];
    
    [controller addTextFieldWithConfigurationHandler:^(UITextField *textField) {
        textField.placeholder = @"Subject";
        textField.text = self.chat.subject;
    }];
    [self presentViewController:controller animated:YES completion:nil];
}

- (void)refreshMap
{
    //Remove old overlays(lines) and annotations(pins)
    [self.mapView removeOverlays:self.mapView.overlays];
    [self.mapView removeAnnotations:self.mapView.annotations];

    for(NSNumber *regId in [self.locationMessagesByRegId allKeys]) {
        //The locations for each user will be connected by a line, each user has its own color.
        self.currentColor = [LocationMapAnnotation colorForRegId:regId];

        NSArray *locationMessages = self.locationMessagesByRegId[regId];
        //coordinates will be used to draw the lines.
        CLLocationCoordinate2D coordinates[locationMessages.count];
        for(int i=0; i < locationMessages.count; i++) {
            //Each message contains a latitude and longitude
            BBMChatMessage *message = locationMessages[i];
            coordinates[i] = CLLocationCoordinate2DMake(message.latitude, message.longitude);

            //Either a pin for each message is displayed or just for the most recent one.
            if(self.showAllLocations || i == locationMessages.count - 1) {
                LocationMapAnnotation *annotation  = [[LocationMapAnnotation alloc] init];
                [self.mapView addAnnotation:annotation];
                annotation.message = message;
            }
        }

        //Draw lines to connect each user's locations
        MKPolyline *polyLine = [MKPolyline polylineWithCoordinates:coordinates count:locationMessages.count];
        [self.mapView addOverlay:polyLine];
    }
}

- (MKOverlayRenderer *)mapView:(MKMapView *)mapView rendererForOverlay:(id<MKOverlay>)overlay
{
    if ([overlay isKindOfClass:[MKPolyline class]])
    {
        MKPolylineRenderer *renderer = [[MKPolylineRenderer alloc] initWithPolyline:overlay];
        renderer.strokeColor = self.currentColor;
        renderer.lineWidth   = 2;

        return renderer;
    }

    return nil;
}

- (void)mapView:(MKMapView *)mapView didUpdateUserLocation:(MKUserLocation *)userLocation
{
    if (!self.hasShownInitialLocation) {
        // Zoom in on the user's current location only for the first update.
        [self.mapView setRegion:MKCoordinateRegionMakeWithDistance(userLocation.coordinate, 500, 500) animated:NO];
        self.hasShownInitialLocation = YES;
        [self observeMessages];
    }
}

#pragma mark - Actions

- (IBAction)toggle:(id)sender
{
    self.showAllLocations = !self.showAllLocations;
    self.showAllLocationsBarButton.title = self.showAllLocations ? @"View last location" : @"View all locations" ;
    [self refreshMap];
}
@end
