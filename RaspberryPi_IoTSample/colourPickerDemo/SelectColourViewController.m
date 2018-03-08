//
//  SelectColourViewController.m
//  colourPickerDemo
//
//  Copyright (c) 2018 BlackBerry.  All Rights Reserved.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

#import "SelectColourViewController.h"
#import "Global.h"

@interface SelectColourViewController ()

@property (nonatomic, strong) UIButton              *closeColourPickerButton;

@end

@implementation SelectColourViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    
    MSColorSelectionView *colorSelectionView = [[MSColorSelectionView alloc] initWithFrame:[[UIScreen mainScreen] bounds]];
    
    self.view = colorSelectionView;
    
    [colorSelectionView setSelectedIndex:1 animated:NO];
    colorSelectionView.delegate = self;
    
    //  Set up the closeColourPickerButton UI.
    self.closeColourPickerButton = [UIButton buttonWithType: UIButtonTypeRoundedRect];
    [self.closeColourPickerButton addTarget: self
                                     action: @selector(pickNewColourButtonPressed:)
                           forControlEvents: UIControlEventTouchUpInside];
    self.closeColourPickerButton.frame = CGRectMake(([self.view frame].size.width / 2) - 115, ([self.view frame].size.height / 4 * 3) - 24, 230, 48);
    [self.closeColourPickerButton setTitle:@"Confirm colour" forState: UIControlStateNormal];
    [self.closeColourPickerButton setExclusiveTouch: YES];
    [self.closeColourPickerButton setTitleColor: [UIColor blackColor] forState: UIControlStateNormal];
    [[self.closeColourPickerButton titleLabel] setFont:[UIFont fontWithName:@"AvenirNext-Regular" size:20.0]];
    [self.view addSubview: self.closeColourPickerButton];
    
}

- (void)colorView:(id<MSColorView>)colorView didChangeColor:(UIColor *)color {
    NSString *colourHex = MSHexStringFromColor(color);
    printf("%s\n", [colourHex UTF8String]);
    
    //  Set shared instance colour for ColourPickerViewController to use as colour to send to peripheral device and to display in UI.
    [self setColor:color];
    [[Global sharedInstance] setChosenColour:color];
}

- (IBAction)pickNewColourButtonPressed:(id)sender
{
    
    //  Transition back to ColourPickerViewController.
    [self dismissViewControllerAnimated:YES completion:nil];
    
}

- (MSColorSelectionView *)colorSelectionView
{
    return (MSColorSelectionView *)self.view;
}

@end
